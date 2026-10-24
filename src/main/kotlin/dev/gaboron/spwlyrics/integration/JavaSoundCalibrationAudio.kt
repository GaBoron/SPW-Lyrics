package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.CalibrationBeatAudio
import dev.gaboron.spwlyrics.application.TimingLatencyCalibration
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*
import kotlin.math.*

/** Only calibration drum beats are emitted here. Song playback remains with SPW. */
internal class JavaSoundCalibrationAudio : CalibrationBeatAudio {
    private val line = AtomicReference<SourceDataLine?>()
    @Volatile private var active = false
    override var device: String = "系统默认输出"; private set
    override fun play(bpm: Int, beats: Int, cancelled: () -> Boolean) {
        val format = AudioFormat(48000f, 16, 1, true, false)
        val output = AudioSystem.getSourceDataLine(format)
        try {
            output.open(format, 4800)
            line.set(output)
            if (cancelled()) return
            val period = 48000 * 60 / bpm
            val lead = TimingLatencyCalibration.LEAD_MS * 48
            val total = lead + beats * period
            val bytes = ByteArray(1920)
            active = true
            output.start()
            var position = 0
            while (position < total && !cancelled() && output.isOpen) {
                val frames = minOf(bytes.size / 2, total - position)
                repeat(frames) { i ->
                    val relative = position + i - lead
                    val inBeat = if (relative >= 0) relative % period else period
                    val seconds = inBeat / 48000.0
                    val sample = if (inBeat < 4800) {
                        val accent = relative / period % 4 == 0
                        val frequency = if (accent) 165.0 else 115.0
                        val drum = sin(2 * PI * (frequency * seconds + 45 * (1 - exp(-seconds * 35)) / 35)) * exp(-seconds * 65)
                        (drum * 18000).roundToInt()
                    } else 0
                    bytes[i * 2] = sample.toByte(); bytes[i * 2 + 1] = (sample shr 8).toByte()
                }
                val written = output.write(bytes, 0, frames * 2)
                if (written <= 0) break
                position += written / 2
            }
            if (!cancelled() && output.isOpen) output.drain()
        } finally {
            active = false
            line.compareAndSet(output, null)
            runCatching { output.stop(); output.flush() }
            output.close()
        }
    }
    override fun positionMs(): Double? = runCatching { line.get()?.takeIf { active && it.isOpen }?.let { it.longFramePosition / 48.0 } }.getOrNull()
    override fun close() { active = false; line.getAndSet(null)?.let { runCatching { it.stop(); it.flush() }; it.close() } }
}
