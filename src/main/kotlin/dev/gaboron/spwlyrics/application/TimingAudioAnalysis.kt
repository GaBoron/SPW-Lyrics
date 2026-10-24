package dev.gaboron.spwlyrics.application

import java.nio.file.Path
import dev.gaboron.spwlyrics.domain.TimingPcm

internal interface TimingAudioDecoder : AutoCloseable {
    fun decode(path: Path, startMs: Long, durationMs: Long, cancelled: () -> Boolean): TimingPcm
    fun cancel()
    override fun close() = cancel()
}
