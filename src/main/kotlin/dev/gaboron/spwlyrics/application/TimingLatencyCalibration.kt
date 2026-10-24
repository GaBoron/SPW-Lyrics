package dev.gaboron.spwlyrics.application

import kotlin.math.abs
import kotlin.math.roundToInt
import dev.gaboron.spwlyrics.domain.InputLatencyCalibration

internal interface CalibrationBeatAudio : AutoCloseable {
    fun play(bpm: Int, beats: Int, cancelled: () -> Boolean)
    fun positionMs(): Double?
    val device: String
}

/** Beat timestamps belong to the output sample clock, never to a repaint or a scheduled UI callback. */
internal class TimingLatencyCalibration(val bpm: Int = 120) {
    init { require(bpm in 60..180) }
    companion object { const val LEAD_MS = 500; const val WARMUP = 4; const val SAMPLES = 16 }
    val periodMs = 60000.0 / bpm
    val offsets = linkedMapOf<Int, Double>()
    fun tap(positionMs: Double): Double? {
        if (!positionMs.isFinite()) return null
        val beat = ((positionMs - LEAD_MS) / periodMs).roundToInt()
        if (beat !in WARMUP until WARMUP + SAMPLES || beat in offsets) return null
        val offset = positionMs - (LEAD_MS + beat * periodMs)
        if (abs(offset) > minOf(220.0, periodMs * .45)) return null
        offsets[beat] = offset
        return offset
    }
    fun result(device: String = ""): InputLatencyCalibration? {
        if (offsets.size < 12) return null
        val median = median(offsets.values.toList())
        val deviation = median(offsets.values.map { abs(it - median) })
        val accepted = offsets.values.filter { abs(it - median) <= maxOf(20.0, deviation * 3) }
        if (accepted.size < 10 || deviation > 50) return null
        return InputLatencyCalibration(median(accepted).roundToInt(), accepted.size, deviation, device)
    }
    private fun median(values: List<Double>): Double = values.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
}
