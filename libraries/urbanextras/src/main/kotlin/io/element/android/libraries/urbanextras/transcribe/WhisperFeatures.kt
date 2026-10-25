/*
 * UrbanChat extras: audio decoding and the log-mel spectrogram that Whisper expects.
 */

package io.element.android.libraries.urbanextras.transcribe

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

internal object WhisperFeatures {
    const val SAMPLE_RATE = 16_000
    const val N_FFT = 400
    const val HOP = 160
    const val N_MELS = 80
    const val CHUNK_SAMPLES = 30 * SAMPLE_RATE
    const val N_FRAMES = 3000

    /** Decodes any audio file Android can play into 16 kHz mono floats in -1..1. */
    fun decodeToMono16k(path: String): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i
                break
            }
        }
        require(track >= 0) { "No audio track" }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcm = ArrayList<FloatArray>()
        var total = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex >= 0 -> {
                    val out = codec.getOutputBuffer(outIndex)!!
                    out.order(ByteOrder.nativeOrder())
                    val shorts = out.asShortBuffer()
                    val count = info.size / 2
                    val frames = count / channels
                    val mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += shorts.get(f * channels + c)
                        mono[f] = sum / channels / 32768f
                    }
                    pcm.add(mono)
                    total += frames
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val all = FloatArray(total)
        var offset = 0
        pcm.forEach {
            it.copyInto(all, offset)
            offset += it.size
        }
        return resample(all, sampleRate, SAMPLE_RATE)
    }

    /** Resamples by averaging the source samples that fall into each output sample (a simple low-pass), which is enough for speech. */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return input
        val ratio = from.toDouble() / to
        val outSize = (input.size / ratio).toInt()
        val out = FloatArray(outSize)
        for (i in 0 until outSize) {
            val start = (i * ratio).toInt()
            val end = minOf(input.size, max(start + 1, ((i + 1) * ratio).toInt()))
            var sum = 0f
            for (j in start until end) sum += input[j]
            out[i] = sum / (end - start)
        }
        return out
    }

    private val melFilters: Array<FloatArray> by lazy { buildMelFilters() }
    private val cosTable: Array<FloatArray> by lazy { Array(N_FFT / 2 + 1) { k -> FloatArray(N_FFT) { n -> cos(2.0 * PI * k * n / N_FFT).toFloat() } } }
    private val sinTable: Array<FloatArray> by lazy { Array(N_FFT / 2 + 1) { k -> FloatArray(N_FFT) { n -> sin(2.0 * PI * k * n / N_FFT).toFloat() } } }
    private val window: FloatArray by lazy { FloatArray(N_FFT) { (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat() } }

    /** Log-mel spectrogram of one 30 second window (shorter input is padded with silence): 80 x 3000 values, mel-major. */
    fun logMel(samples: FloatArray, offset: Int): FloatArray {
        val chunk = FloatArray(CHUNK_SAMPLES)
        val length = minOf(CHUNK_SAMPLES, samples.size - offset).coerceAtLeast(0)
        System.arraycopy(samples, offset, chunk, 0, length)

        val pad = N_FFT / 2
        val padded = FloatArray(CHUNK_SAMPLES + 2 * pad)
        for (i in padded.indices) {
            // Reflect at the edges, as the reference implementation does.
            val src = i - pad
            val idx = when {
                src < 0 -> -src
                src >= CHUNK_SAMPLES -> 2 * (CHUNK_SAMPLES - 1) - src
                else -> src
            }
            padded[i] = chunk[idx.coerceIn(0, CHUNK_SAMPLES - 1)]
        }

        val mel = FloatArray(N_MELS * N_FRAMES)
        val bins = N_FFT / 2 + 1
        val power = FloatArray(bins)
        val usedFrames = ((length + HOP - 1) / HOP + 2).coerceAtMost(N_FRAMES)
        val frame = FloatArray(N_FFT)
        for (t in 0 until N_FRAMES) {
            if (t >= usedFrames) break
            val start = t * HOP
            for (n in 0 until N_FFT) frame[n] = padded[start + n] * window[n]
            for (k in 0 until bins) {
                var re = 0f
                var im = 0f
                val c = cosTable[k]
                val s = sinTable[k]
                for (n in 0 until N_FFT) {
                    re += frame[n] * c[n]
                    im -= frame[n] * s[n]
                }
                power[k] = re * re + im * im
            }
            for (m in 0 until N_MELS) {
                var sum = 0f
                val f = melFilters[m]
                for (k in 0 until bins) sum += f[k] * power[k]
                mel[m * N_FRAMES + t] = sum
            }
        }
        // Frames that are only padding (silence) all have the value of an empty frame.
        for (m in 0 until N_MELS) {
            for (t in usedFrames until N_FRAMES) mel[m * N_FRAMES + t] = 0f
        }

        var maxLog = -Float.MAX_VALUE
        for (i in mel.indices) {
            val v = log10(max(mel[i], 1e-10f))
            mel[i] = v
            if (v > maxLog) maxLog = v
        }
        val floor = maxLog - 8f
        for (i in mel.indices) mel[i] = (max(mel[i], floor) + 4f) / 4f
        return mel
    }

    private fun hzToMel(hz: Double): Double {
        val fSp = 200.0 / 3
        val minLogHz = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep = ln(6.4) / 27.0
        return if (hz >= minLogHz) minLogMel + ln(hz / minLogHz) / logStep else hz / fSp
    }

    private fun melToHz(mel: Double): Double {
        val fSp = 200.0 / 3
        val minLogHz = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep = ln(6.4) / 27.0
        return if (mel >= minLogMel) minLogHz * kotlin.math.exp(logStep * (mel - minLogMel)) else mel * fSp
    }

    private fun buildMelFilters(): Array<FloatArray> {
        val bins = N_FFT / 2 + 1
        val fftFreqs = DoubleArray(bins) { it * SAMPLE_RATE.toDouble() / N_FFT }
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melPoints = DoubleArray(N_MELS + 2) { melToHz(melMin + (melMax - melMin) * it / (N_MELS + 1)) }
        return Array(N_MELS) { m ->
            val lower = melPoints[m]
            val centre = melPoints[m + 1]
            val upper = melPoints[m + 2]
            val enorm = 2.0 / (upper - lower)
            FloatArray(bins) { k ->
                val f = fftFreqs[k]
                val rise = (f - lower) / (centre - lower)
                val fall = (upper - f) / (upper - centre)
                (max(0.0, minOf(rise, fall)) * enorm).toFloat()
            }
        }
    }
}
