/*
 * UrbanChat extras: Whisper speech recognition running on the phone through ONNX Runtime.
 */

package io.element.android.libraries.urbanextras.transcribe

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

internal class WhisperEngine(modelDir: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
    private val encoder = env.createSession(File(modelDir, WhisperModels.ENCODER).absolutePath, options)
    private val decoder = env.createSession(File(modelDir, WhisperModels.DECODER).absolutePath, options)
    private val tokenizer = WhisperTokenizer(File(modelDir, WhisperModels.VOCAB))

    private val pastNames = decoder.inputNames.filter { it.startsWith("past_key_values.") }
    private val pastDims: Map<String, Pair<Long, Long>> = pastNames.associateWith { name ->
        val shape = (decoder.inputInfo[name]!!.info as TensorInfo).shape
        // [batch, heads, sequence, head_dim]
        shape[1] to shape[3]
    }

    /** Turns [samples] (16 kHz mono) into text. [languages] limits the language guess; an empty list lets the model choose. */
    fun transcribe(samples: FloatArray, languages: List<String>, onProgress: (Float) -> Unit): String {
        val text = StringBuilder()
        var offset = 0
        var detected: String? = languages.singleOrNull()
        val chunks = ((samples.size + WhisperFeatures.CHUNK_SAMPLES - 1) / WhisperFeatures.CHUNK_SAMPLES).coerceAtLeast(1)
        var index = 0
        while (offset < samples.size || index == 0) {
            val mel = WhisperFeatures.logMel(samples, offset)
            val hidden = encode(mel)
            try {
                val language = detected ?: detectLanguage(hidden, languages).also { detected = it }
                val part = decode(hidden, language)
                if (part.isNotBlank()) {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(part.trim())
                }
            } finally {
                hidden.close()
            }
            offset += WhisperFeatures.CHUNK_SAMPLES
            index++
            onProgress(index / chunks.toFloat())
            if (samples.isEmpty()) break
        }
        return text.toString()
    }

    private fun encode(mel: FloatArray): OnnxTensor {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(mel), longArrayOf(1, WhisperFeatures.N_MELS.toLong(), WhisperFeatures.N_FRAMES.toLong())).use { input ->
            encoder.run(mapOf(encoder.inputNames.first() to input)).use { result ->
                val output = result[0] as OnnxTensor
                val data = output.floatBuffer
                val copy = FloatArray(data.remaining())
                data.get(copy)
                return OnnxTensor.createTensor(env, FloatBuffer.wrap(copy), output.info.shape)
            }
        }
    }

    private fun detectLanguage(hidden: OnnxTensor, allowed: List<String>): String {
        val (logits, present) = runDecoder(hidden, longArrayOf(WhisperTokenizer.SOT), null, firstStep = true)
        present.values.forEach { it.close() }
        val candidates = if (allowed.isEmpty()) WhisperTokenizer.LANGUAGES else allowed
        var best = candidates.first()
        var bestScore = -Float.MAX_VALUE
        for (code in candidates) {
            val id = WhisperTokenizer.languageToken(code) ?: continue
            if (logits[id] > bestScore) {
                bestScore = logits[id]
                best = code
            }
        }
        return best
    }

    private fun decode(hidden: OnnxTensor, language: String): String {
        val prompt = longArrayOf(
            WhisperTokenizer.SOT,
            (WhisperTokenizer.languageToken(language) ?: WhisperTokenizer.languageToken("en")!!).toLong(),
            WhisperTokenizer.TRANSCRIBE,
            WhisperTokenizer.NO_TIMESTAMPS,
        ).map { it.toInt() }
        val generated = ArrayList<Int>()
        var past: Map<String, OnnxTensor>? = null
        var input = prompt.map { it.toLong() }.toLongArray()
        try {
            for (step in 0 until MAX_TOKENS) {
                val (logits, present) = runDecoder(hidden, input, past, firstStep = past == null)
                // Keep the encoder part of the cache from the first step, and replace the decoder part every step.
                val merged = HashMap<String, OnnxTensor>()
                past?.forEach { (name, tensor) -> if (name.contains(".encoder.")) merged[name] = tensor }
                present.forEach { (name, tensor) ->
                    val pastName = name.replace("present.", "past_key_values.")
                    if (name.contains(".decoder.") || past == null) {
                        past?.get(pastName)?.takeIf { name.contains(".decoder.") }?.close()
                        merged[pastName] = tensor
                    } else {
                        tensor.close()
                    }
                }
                past = merged

                // Pick the most likely text token. Special tokens (other than the end marker) are never chosen.
                var best = WhisperTokenizer.EOT.toInt()
                var bestScore = logits[best]
                for (id in 0 until WhisperTokenizer.EOT.toInt()) {
                    if (logits[id] > bestScore) {
                        bestScore = logits[id]
                        best = id
                    }
                }
                if (best == WhisperTokenizer.EOT.toInt()) break
                generated.add(best)
                if (isLooping(generated)) break
                input = longArrayOf(best.toLong())
            }
        } finally {
            past?.values?.forEach { it.close() }
        }
        return tokenizer.decode(generated)
    }

    /** Stops runaway repetition, which small models can fall into on unclear audio. */
    private fun isLooping(tokens: List<Int>): Boolean {
        if (tokens.size < 24) return false
        for (size in 1..6) {
            var repeats = 0
            while (repeats < 8 && tokens.size >= (repeats + 2) * size &&
                (0 until size).all { tokens[tokens.size - 1 - it] == tokens[tokens.size - 1 - it - (repeats + 1) * size] }
            ) {
                repeats++
            }
            if (repeats >= 7) return true
        }
        return false
    }

    /** Runs the decoder once. Returns the logits of the last position and the cache the run produced. */
    private fun runDecoder(
        hidden: OnnxTensor,
        tokens: LongArray,
        past: Map<String, OnnxTensor>?,
        firstStep: Boolean,
    ): Pair<FloatArray, Map<String, OnnxTensor>> {
        val inputs = HashMap<String, OnnxTensor>()
        val toClose = ArrayList<OnnxTensor>()
        try {
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())).also(toClose::add)
            inputs["input_ids"] = ids
            inputs["encoder_hidden_states"] = hidden
            inputs["use_cache_branch"] = OnnxTensor.createTensor(env, booleanArrayOf(!firstStep)).also(toClose::add)
            for (name in pastNames) {
                inputs[name] = past?.get(name) ?: run {
                    val (heads, headDim) = pastDims.getValue(name)
                    OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, heads, 0, headDim)).also(toClose::add)
                }
            }
            val result = decoder.run(inputs)
            result.use {
                val logitsTensor = it.get("logits").get() as OnnxTensor
                val shape = logitsTensor.info.shape
                val vocab = shape[2].toInt()
                val lastPosition = (shape[1].toInt() - 1)
                val buffer = logitsTensor.floatBuffer
                val logits = FloatArray(vocab)
                buffer.position(lastPosition * vocab)
                buffer.get(logits, 0, vocab)

                val present = HashMap<String, OnnxTensor>()
                for (name in decoder.outputNames) {
                    if (!name.startsWith("present.")) continue
                    val tensor = it.get(name).get() as OnnxTensor
                    val data = tensor.floatBuffer
                    val copy = FloatArray(data.remaining())
                    data.get(copy)
                    present[name] = OnnxTensor.createTensor(env, FloatBuffer.wrap(copy), tensor.info.shape)
                }
                return logits to present
            }
        } finally {
            toClose.forEach { it.close() }
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
        options.close()
    }

    private companion object {
        const val MAX_TOKENS = 224
    }
}

/** Reads Whisper's `vocab.json` and turns generated token numbers back into text. */
internal class WhisperTokenizer(vocabFile: File) {
    private val idToToken: Array<String?>
    private val byteDecoder: Map<Char, Int>

    init {
        val json = JSONObject(vocabFile.readText())
        val size = (json.keys().asSequence().maxOf { json.getInt(it) }) + 1
        idToToken = arrayOfNulls(size)
        json.keys().forEach { idToToken[json.getInt(it)] = it }
        byteDecoder = buildByteDecoder()
    }

    fun decode(ids: List<Int>): String {
        val bytes = java.io.ByteArrayOutputStream()
        for (id in ids) {
            if (id >= EOT) continue
            val token = idToToken.getOrNull(id) ?: continue
            for (ch in token) byteDecoder[ch]?.let(bytes::write)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun buildByteDecoder(): Map<Char, Int> {
        // GPT-2 byte-level BPE: printable bytes map to themselves, the rest to code points from 256 up.
        val printable = ((33..126) + (161..172) + (174..255)).toMutableList()
        val chars = printable.toMutableList()
        var extra = 0
        for (b in 0..255) {
            if (b !in printable) {
                printable.add(b)
                chars.add(256 + extra)
                extra++
            }
        }
        return printable.indices.associate { chars[it].toChar() to printable[it] }
    }

    companion object {
        const val EOT = 50257L
        const val SOT = 50258L
        const val TRANSCRIBE = 50359L
        const val NO_TIMESTAMPS = 50363L

        val LANGUAGES = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi", "he", "uk",
            "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn",
            "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn",
            "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my",
            "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su",
        )

        fun languageToken(code: String): Int? = LANGUAGES.indexOf(code).takeIf { it >= 0 }?.let { 50259 + it }
    }
}
