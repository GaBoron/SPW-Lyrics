package dev.gaboron.spwlyrics.application

import java.nio.file.Path

internal data class TimingPlaybackSnapshot(val trackId: String, val path: String, val positionMs: Long,
    val durationMs: Long, val playing: Boolean, val rate: Float, val seekGeneration: Long = 0)

internal interface TimingPlayback {
    fun read(expectedPath: Path): TimingPlaybackSnapshot
    fun play(expectedPath: Path)
    fun pause(expectedPath: Path)
    fun seek(expectedPath: Path, positionMs: Long)
    fun rate(expectedPath: Path, value: Float)
}
