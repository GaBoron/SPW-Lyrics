package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
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
    val encoded: String? = null,
    val savedAtEpochMs: Long,
    val modelVersion: Int = LYRICS_CACHE_MODEL_VERSION,
    val encoderVersion: Int = SpwLyricsEncoder.VERSION,
    val selectionKey: String = "",
    val candidate: LyricsCandidate? = null,
)

@Serializable
data class ManualOverride(
    val local: Boolean,
    val source: LyricsSource? = null,
    val candidate: LyricsCandidate? = null,
    val modelVersion: Int = MANUAL_OVERRIDE_MODEL_VERSION,
)

const val LYRICS_CACHE_MODEL_VERSION = 6
const val MANUAL_OVERRIDE_MODEL_VERSION = 2

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
    private val overrides = object : LinkedHashMap<String, ManualOverride?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ManualOverride?>?): Boolean = size > 64
    }
    private val trackLocks = Array(32) { Any() }

    init {
        Files.createDirectories(root)
    }

    override fun getLyrics(query: TrackQuery): CachedLyrics? {
        val key = query.key
        synchronized(memory) { memory[key]?.takeIf(::validLyrics)?.let { return it } }
        return synchronized(trackLock(key)) {
            synchronized(memory) { memory[key]?.takeIf(::validLyrics)?.let { return it } }
            val stored = read(layout.lyrics(query))?.let { runCatching { json.decodeFromString<CachedLyrics>(it) }.getOrNull() }
                ?.takeIf(::validLyrics)
                ?.let { if (it.encoded == null) it.copy(encoded = SpwLyricsEncoder.encode(it.document)) else it }
            stored?.let { synchronized(memory) { memory[key] = it } }
            stored
        }
    }

    override fun putLyrics(query: TrackQuery, lyrics: CachedLyrics) {
        val key = query.key
        synchronized(trackLock(key)) {
            synchronized(memory) { memory[key] = lyrics }
            write(layout.lyrics(query), json.encodeToString(lyrics))
        }
    }

    override fun removeLyrics(query: TrackQuery) {
        val key = query.key
        synchronized(trackLock(key)) {
            synchronized(memory) { memory.remove(key) }
            Files.deleteIfExists(layout.lyrics(query))
        }
    }

    override fun getOverride(query: TrackQuery): ManualOverride? {
        val key = query.key
        synchronized(overrides) { if (overrides.containsKey(key)) return overrides[key] }
        return synchronized(trackLock(key)) {
            synchronized(overrides) { if (overrides.containsKey(key)) return overrides[key] }
            val stored = read(layout.override(query))
                ?.let { runCatching { json.decodeFromString<ManualOverride>(it) }.getOrNull() }
                ?.takeIf { it.modelVersion == MANUAL_OVERRIDE_MODEL_VERSION }
            synchronized(overrides) { overrides[key] = stored }
            stored
        }
    }

    override fun putOverride(query: TrackQuery, override: ManualOverride) {
        val key = query.key
        synchronized(trackLock(key)) {
            write(layout.override(query), json.encodeToString(override))
            synchronized(overrides) { overrides[key] = override }
        }
    }

    override fun removeOverride(query: TrackQuery) {
        val key = query.key
        synchronized(trackLock(key)) {
            Files.deleteIfExists(layout.override(query))
            synchronized(overrides) { overrides[key] = null }
        }
    }

    private fun trackLock(key: String): Any = trackLocks[(key.hashCode() and Int.MAX_VALUE) % trackLocks.size]

    private fun validLyrics(value: CachedLyrics): Boolean =
        value.modelVersion == LYRICS_CACHE_MODEL_VERSION && value.encoderVersion == SpwLyricsEncoder.VERSION &&
            (value.document.format != LyricsFormat.TTML || value.document.rawTtml != null) &&
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
