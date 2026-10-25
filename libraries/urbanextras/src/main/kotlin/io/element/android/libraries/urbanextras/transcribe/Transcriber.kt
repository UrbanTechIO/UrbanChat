/*
 * UrbanChat extras: voice message to text, fully on the phone.
 */

package io.element.android.libraries.urbanextras.transcribe

import android.content.Context
import io.element.android.libraries.urbanextras.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface TranscriptState {
    data class Running(val progress: Float) : TranscriptState
    data class Done(val text: String) : TranscriptState
    data class Failed(val message: String) : TranscriptState
}

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

enum class WhisperSize(val label: String, val repo: String, val encoderFile: String, val decoderFile: String, val approxMb: Int) {
    BASE("Base (faster)", "onnx-community/whisper-base", "onnx/encoder_model_uint8.onnx", "onnx/decoder_model_merged_uint8.onnx", 77),
    SMALL("Small (better, especially Arabic)", "onnx-community/whisper-small", "onnx/encoder_model_uint8.onnx", "onnx/decoder_model_merged_uint8.onnx", 250),
}

internal object WhisperModels {
    const val ENCODER = "encoder.onnx"
    const val DECODER = "decoder.onnx"
    const val VOCAB = "vocab.json"

    // "-u8": a different file set than the first version, so models downloaded earlier are not reused.
    fun dir(context: Context, size: WhisperSize) = File(context.filesDir, "whisper/${size.name.lowercase()}-u8")

    fun isReady(context: Context, size: WhisperSize): Boolean {
        val dir = dir(context, size)
        return File(dir, ENCODER).exists() && File(dir, DECODER).exists() && File(dir, VOCAB).exists() && File(dir, ".complete").exists()
    }
}

/**
 * Turns voice messages into text on the phone with Whisper. The speech model is downloaded once, when the person first asks for a
 * transcript and chooses a size and the languages they speak.
 */
class Transcriber private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsStore = JsonFileStore(context, "transcribe_settings.json")
    private val transcriptStore = JsonFileStore(context, "transcripts.json")

    private val _transcripts = MutableStateFlow(readTranscripts())
    val transcripts: StateFlow<Map<String, TranscriptState>> = _transcripts

    private val _size = MutableStateFlow(readSize())
    val size: StateFlow<WhisperSize> = _size

    private val _languages = MutableStateFlow(readLanguages())
    val languages: StateFlow<List<String>> = _languages

    private val _model = MutableStateFlow<ModelState>(if (WhisperModels.isReady(context, _size.value)) ModelState.Ready else ModelState.NotDownloaded)
    val model: StateFlow<ModelState> = _model

    /** True while the person must first choose a model and languages before anything can be transcribed. */
    private val _needsSetup = MutableStateFlow(false)
    val needsSetup: StateFlow<Boolean> = _needsSetup

    private var pending: Pair<String, suspend () -> Result<String>>? = null
    private var engine: WhisperEngine? = null
    private var engineSize: WhisperSize? = null

    fun transcribe(eventKey: String, audioFile: suspend () -> Result<String>) {
        if (!WhisperModels.isReady(context, _size.value)) {
            pending = eventKey to audioFile
            _needsSetup.value = true
            return
        }
        run(eventKey, audioFile)
    }

    /** Opens the model and language chooser from the settings, without a transcription waiting for it. */
    fun openSettings() {
        pending = null
        _needsSetup.value = true
    }

    fun dismissSetup() {
        pending = null
        _needsSetup.value = false
    }

    fun chooseSize(size: WhisperSize) {
        _size.value = size
        _model.value = if (WhisperModels.isReady(context, size)) ModelState.Ready else ModelState.NotDownloaded
        saveSettings()
    }

    fun setLanguages(codes: List<String>) {
        _languages.value = codes
        saveSettings()
    }

    fun clearTranscript(eventKey: String) {
        _transcripts.value = _transcripts.value - eventKey
        persistTranscripts()
    }

    fun download() {
        val size = _size.value
        if (_model.value is ModelState.Downloading) return
        scope.launch {
            try {
                _model.value = ModelState.Downloading(0, 0)
                downloadModel(size)
                _model.value = ModelState.Ready
                _needsSetup.value = false
                pending?.let { (key, file) ->
                    pending = null
                    run(key, file)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Could not download the speech model")
                _model.value = ModelState.Failed(t.message ?: "Download failed")
            }
        }
    }

    private fun run(eventKey: String, audioFile: suspend () -> Result<String>) {
        if (_transcripts.value[eventKey] is TranscriptState.Running) return
        _transcripts.value = _transcripts.value + (eventKey to TranscriptState.Running(0f))
        scope.launch {
            try {
                val path = audioFile().getOrThrow()
                val samples = WhisperFeatures.decodeToMono16k(path)
                val engine = engine()
                val text = engine.transcribe(samples, _languages.value) { progress ->
                    _transcripts.value = _transcripts.value + (eventKey to TranscriptState.Running(progress))
                }
                _transcripts.value = _transcripts.value + (eventKey to TranscriptState.Done(text.ifBlank { "(no speech found)" }))
                persistTranscripts()
            } catch (t: Throwable) {
                Timber.e(t, "Transcription failed")
                _transcripts.value = _transcripts.value + (eventKey to TranscriptState.Failed(t.message ?: "Transcription failed"))
            }
        }
    }

    @Synchronized
    private fun engine(): WhisperEngine {
        val wanted = _size.value
        if (engine == null || engineSize != wanted) {
            engine?.close()
            engine = WhisperEngine(WhisperModels.dir(context, wanted))
            engineSize = wanted
        }
        return engine!!
    }

    private fun downloadModel(size: WhisperSize) {
        val dir = WhisperModels.dir(context, size).apply { mkdirs() }
        File(dir, ".complete").delete()
        val base = "https://huggingface.co/${size.repo}/resolve/main/"
        val files = listOf(
            size.encoderFile to WhisperModels.ENCODER,
            size.decoderFile to WhisperModels.DECODER,
            "vocab.json" to WhisperModels.VOCAB,
        )
        val sizes = files.map { (remote, _) -> contentLength(base + remote) }
        val total = sizes.sum()
        var done = 0L
        files.forEachIndexed { index, (remote, local) ->
            val target = File(dir, local)
            val part = File(dir, "$local.part")
            val connection = URL(base + remote).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            try {
                check(connection.responseCode == 200) { "Download failed (HTTP ${connection.responseCode})" }
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (done - lastReport > 512 * 1024) {
                                lastReport = done
                                _model.value = ModelState.Downloading(done, total)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            check(sizes[index] <= 0 || part.length() == sizes[index]) { "Download incomplete" }
            target.delete()
            check(part.renameTo(target)) { "Could not save the model" }
        }
        File(dir, ".complete").writeText("ok")
    }

    private fun contentLength(url: String): Long {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        return try {
            connection.contentLengthLong
        } finally {
            connection.disconnect()
        }
    }

    private fun readSize(): WhisperSize =
        runCatching { WhisperSize.valueOf(settingsStore.readObject().optString("size")) }.getOrDefault(WhisperSize.SMALL)

    private fun readLanguages(): List<String> {
        val array = settingsStore.readObject().optJSONArray("languages") ?: return listOf("ar", "en")
        return List(array.length()) { array.getString(it) }
    }

    private fun saveSettings() {
        settingsStore.writeObject(JSONObject().put("size", _size.value.name).put("languages", JSONArray(_languages.value)))
    }

    private fun readTranscripts(): Map<String, TranscriptState> {
        val obj = transcriptStore.readObject()
        val out = HashMap<String, TranscriptState>()
        obj.keys().forEach { out[it] = TranscriptState.Done(obj.getString(it)) }
        return out
    }

    private fun persistTranscripts() {
        val obj = JSONObject()
        _transcripts.value.forEach { (key, value) -> if (value is TranscriptState.Done) obj.put(key, value.text) }
        transcriptStore.writeObject(obj)
    }

    companion object {
        @Volatile
        private var instance: Transcriber? = null

        fun get(context: Context): Transcriber = instance ?: synchronized(this) {
            instance ?: Transcriber(context.applicationContext).also { instance = it }
        }
    }
}
