package dev.gaboron.spwlyrics.domain

internal data class TimingPcm(val startMs: Long, val sampleRate: Int, val samples: FloatArray)

internal data class TimingAudioDependency(val available: Boolean, val installUrl: String = "", val installLabel: String = "")

/** A bounded time window, with log-frequency bins stored from high to low frequency. */
internal data class TimingSpectrum(
    val startMs: Long, val durationMs: Long, val columns: Int, val bands: Int,
    val levels: ByteArray, val peaks: FloatArray,
) {
    val endMs get() = startMs + durationMs
}

internal data class InputLatencyCalibration(val delayMs: Int, val used: Int, val spreadMs: Double,
    val device: String = "", val savedAt: Long = System.currentTimeMillis())
