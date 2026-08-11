package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CachedLyrics(
    val document: LyricsDocument,
    val encoded: String,
    val savedAtEpochMs: Long,
    val modelVersion: Int = CACHE_MODEL_VERSION,
    val encoderVersion: Int = SpwLyricsEncoder.VERSION,
)

@Serializable
data class ManualOverride(
    val local: Boolean,
    val source: LyricsSource? = null,
    val candidate: LyricsCandidate? = null,
    val modelVersion: Int = CACHE_MODEL_VERSION,
)

const val CACHE_MODEL_VERSION = 2

interface LyricsCache {
    fun getLyrics(query: TrackQuery): CachedLyrics?
    fun putLyrics(query: TrackQuery, lyrics: CachedLyrics)
    fun removeLyrics(query: TrackQuery)
    fun getOverride(query: TrackQuery): ManualOverride?
    fun putOverride(query: TrackQuery, override: ManualOverride)
    fun removeOverride(query: TrackQuery)
}

class FileLyricsCache(
    private val root: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val successTtl: Duration = Duration.ofDays(30),
) : LyricsCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val layout = ReadableCacheLayout(root)
    private val memory = object : LinkedHashMap<String, CachedLyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLyrics>?): Boolean = size > 64
    }

    init {
        Files.createDirectories(root)
    }

    @Synchronized
    override fun getLyrics(query: TrackQuery): CachedLyrics? {
        memory[query.key]?.takeIf(::validLyrics)?.let { return it }
        val stored = read(layout.lyrics(query))?.let { runCatching { json.decodeFromString<CachedLyrics>(it) }.getOrNull() }
            ?.takeIf(::validLyrics)
        stored?.let { memory[query.key] = it }
        return stored
    }

    @Synchronized
    override fun putLyrics(query: TrackQuery, lyrics: CachedLyrics) {
        memory[query.key] = lyrics
        write(layout.lyrics(query), json.encodeToString(lyrics))
    }

    @Synchronized
    override fun removeLyrics(query: TrackQuery) {
        memory.remove(query.key)
        Files.deleteIfExists(layout.lyrics(query))
    }

    @Synchronized
    override fun getOverride(query: TrackQuery): ManualOverride? = read(layout.override(query))
        ?.let { runCatching { json.decodeFromString<ManualOverride>(it) }.getOrNull() }
        ?.takeIf { it.modelVersion == CACHE_MODEL_VERSION }

    @Synchronized
    override fun putOverride(query: TrackQuery, override: ManualOverride) {
        write(layout.override(query), json.encodeToString(override))
        memory.remove(query.key)
    }

    @Synchronized
    override fun removeOverride(query: TrackQuery) {
        Files.deleteIfExists(layout.override(query))
        memory.remove(query.key)
    }

    private fun validLyrics(value: CachedLyrics): Boolean =
        value.modelVersion == CACHE_MODEL_VERSION && value.encoderVersion == SpwLyricsEncoder.VERSION &&
            !expired(value.savedAtEpochMs, successTtl)

    private fun expired(epochMs: Long, ttl: Duration): Boolean =
        Instant.ofEpochMilli(epochMs).plus(ttl).isBefore(clock.instant())

    private fun read(path: Path): String? = if (Files.isRegularFile(path)) {
        runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull()
    } else {
        null
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
