package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface LyricsDelaySettings {
    fun get(query: TrackQuery): Int
    fun set(query: TrackQuery, delayMs: Int)

    companion object {
        const val MIN_MS = -60_000
        const val MAX_MS = 60_000
    }
}

/** Song-specific settings live outside the disposable generated-lyrics cache. */
class FileLyricsDelaySettings(private val directory: Path) : LyricsDelaySettings {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val values = mutableMapOf<String, Int>()

    @Synchronized
    override fun get(query: TrackQuery): Int = values.getOrPut(query.key) {
        runCatching {
            json.decodeFromString<Entry>(Files.readString(file(query))).delayMs
                .coerceIn(LyricsDelaySettings.MIN_MS, LyricsDelaySettings.MAX_MS)
        }.getOrDefault(0)
    }

    @Synchronized
    override fun set(query: TrackQuery, delayMs: Int) {
        require(delayMs in LyricsDelaySettings.MIN_MS..LyricsDelaySettings.MAX_MS) { "歌词延迟须在 -60000 到 60000 毫秒之间。" }
        Files.createDirectories(directory)
        val target = file(query)
        val pending = target.resolveSibling(target.fileName.toString() + ".pending")
        Files.writeString(pending, json.encodeToString(Entry(query.title, query.artists, delayMs)))
        try {
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
        }
        values[query.key] = delayMs
    }

    private fun file(query: TrackQuery) = directory.resolve("${query.key}.json")

    @Serializable
    private data class Entry(val title: String, val artists: List<String>, val delayMs: Int)
}
