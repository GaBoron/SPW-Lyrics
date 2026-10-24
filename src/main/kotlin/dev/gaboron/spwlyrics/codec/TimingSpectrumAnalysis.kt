package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.TimingPcm
import dev.gaboron.spwlyrics.domain.TimingSpectrum
import kotlin.math.*

/** Windowed STFT, independent of decoding, the host and Compose. */
internal object TimingSpectrumAnalysis {
    const val FFT_SIZE = 1024
    const val BANDS = 128
    fun analyze(pcm: TimingPcm, cancelled: () -> Boolean = { false }): TimingSpectrum {
        require(pcm.sampleRate > 0 && pcm.samples.isNotEmpty()) { "此时间段没有可分析的音频。" }
        val hop = maxOf(128, ceil(pcm.samples.size / 2000.0).toInt())
        val columns = (pcm.samples.size + hop - 1) / hop
        val levels = ByteArray(columns * BANDS)
        val peaks = FloatArray(columns)
        val window = DoubleArray(FFT_SIZE) { .5 - .5 * cos(2 * PI * it / (FFT_SIZE - 1)) }
        val scale = 2 / window.sum()
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val frequencies = IntArray(BANDS) { band ->
            (20 * exp(ln(pcm.sampleRate / 40.0) * band / (BANDS - 1)) * FFT_SIZE / pcm.sampleRate).roundToInt().coerceIn(1, FFT_SIZE / 2)
        }
        repeat(columns) { column ->
            if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException()
            val center = column * hop
            repeat(FFT_SIZE) { i -> real[i] = pcm.samples.getOrElse(center + i - FFT_SIZE / 2) { 0f } * window[i] }
            imaginary.fill(0.0)
            fft(real, imaginary)
            repeat(BANDS) { band ->
                val bin = frequencies[band]
                val amplitude = hypot(real[bin], imaginary[bin]) * scale
                val decibels = 20 * log10(maxOf(amplitude, 1e-6))
                levels[(BANDS - 1 - band) * columns + column] = (((decibels + 90) / 75).coerceIn(0.0, 1.0) * 255).roundToInt().toByte()
            }
            for (i in center until minOf(center + hop, pcm.samples.size)) peaks[column] = maxOf(peaks[column], abs(pcm.samples[i]))
        }
        return TimingSpectrum(pcm.startMs, (pcm.samples.size * 1000L / pcm.sampleRate).coerceAtLeast(1), columns, BANDS, levels, peaks)
    }

    fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val size = real.size
        require(size > 0 && size and (size - 1) == 0 && imaginary.size == size)
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
            }
        }
        var length = 2
        while (length <= size) {
            val angle = -2 * PI / length
            for (offset in 0 until size step length) {
                for (k in 0 until length / 2) {
                    val a = offset + k; val b = a + length / 2
                    val cosine = cos(angle * k); val sine = sin(angle * k)
                    val r = real[b] * cosine - imaginary[b] * sine
                    val im = real[b] * sine + imaginary[b] * cosine
                    real[b] = real[a] - r; imaginary[b] = imaginary[a] - im
                    real[a] += r; imaginary[a] += im
                }
            }
            length *= 2
        }
    }
}
