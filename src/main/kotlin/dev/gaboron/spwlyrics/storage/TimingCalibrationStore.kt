package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.InputLatencyCalibration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.util.Properties

internal interface TimingCalibrationStore {
    fun read(): InputLatencyCalibration?
    fun write(value: InputLatencyCalibration)
}

internal class MemoryTimingCalibrationStore : TimingCalibrationStore {
    private var value: InputLatencyCalibration? = null
    override fun read() = value
    override fun write(value: InputLatencyCalibration) { this.value = value }
}

/** Calibration is user configuration, outside the disposable lyric/audio caches. */
internal class FileTimingCalibrationStore(private val path: Path) : TimingCalibrationStore {
    private var cached = runCatching {
        if (!Files.exists(path)) null else Properties().apply { Files.newInputStream(path).use(::load) }.let {
            InputLatencyCalibration(it.getProperty("delayMs").toInt(), it.getProperty("samples", "0").toInt(),
                it.getProperty("spreadMs", "0").toDouble(), it.getProperty("device", ""), it.getProperty("savedAt", "0").toLong())
                .also { value -> require(value.delayMs in -500..500 && value.spreadMs.isFinite()) }
        }
    }.getOrNull()
    @Synchronized override fun read() = cached
    @Synchronized override fun write(value: InputLatencyCalibration) {
        require(value.delayMs in -500..500 && value.spreadMs.isFinite())
        Files.createDirectories(path.parent)
        val pending = path.resolveSibling(path.fileName.toString() + ".pending")
        val properties = Properties().apply {
            setProperty("delayMs", value.delayMs.toString()); setProperty("samples", value.used.toString())
            setProperty("spreadMs", value.spreadMs.toString()); setProperty("device", value.device); setProperty("savedAt", value.savedAt.toString())
        }
        Files.newOutputStream(pending).use { properties.store(it, "Lyric timing input calibration") }
        try { Files.move(pending, path, ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(pending, path, REPLACE_EXISTING) }
        cached = value
    }
}
