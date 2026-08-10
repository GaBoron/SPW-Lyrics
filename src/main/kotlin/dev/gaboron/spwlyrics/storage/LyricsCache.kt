package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
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
data class CachedSearch(
    val candidates: List<LyricsCandidate>,
    val savedAtEpochMs: Long,
    val modelVersion: Int = CACHE_MODEL_VERSION,
)

@Serializable
data class ManualOverride(
    val local: Boolean,
    val source: LyricsSource? = null,
    val candidate: LyricsCandidate? = null,
    val modelVersion: Int = CACHE_MODEL_VERSION,
)

const val CACHE_MODEL_VERSION = 1

interface LyricsCache {
    fun getLyrics(trackKey: String): CachedLyrics?
    fun putLyrics(trackKey: String, lyrics: CachedLyrics)
    fun getSearch(source: LyricsSource, query: String): List<LyricsCandidate>?
    fun putSearch(source: LyricsSource, query: String, candidates: List<LyricsCandidate>)
    fun hasRecentMiss(trackKey: String): Boolean
    fun putMiss(trackKey: String)
    fun clearMiss(trackKey: String)
    fun getOverride(trackKey: String): ManualOverride?
    fun putOverride(trackKey: String, override: ManualOverride)
}

class FileLyricsCache(
    private val root: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val successTtl: Duration = Duration.ofDays(30),
    private val searchTtl: Duration = Duration.ofHours(6),
    private val missTtl: Duration = Duration.ofHours(24),
) : LyricsCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val memory = object : LinkedHashMap<String, CachedLyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLyrics>?): Boolean = size > 64
    }

    init {
        Files.createDirectories(root)
    }

    @Synchronized
    override fun getLyrics(trackKey: String): CachedLyrics? {
        memory[trackKey]?.takeIf(::validLyrics)?.let { return it }
        val stored = read(cachePath("lyrics", trackKey))?.let { runCatching { json.decodeFromString<CachedLyrics>(it) }.getOrNull() }
            ?.takeIf(::validLyrics)
        stored?.let { memory[trackKey] = it }
        return stored
    }

    @Synchronized
    override fun putLyrics(trackKey: String, lyrics: CachedLyrics) {
        memory[trackKey] = lyrics
        write(cachePath("lyrics", trackKey), json.encodeToString(lyrics))
        clearMiss(trackKey)
    }

    override fun getSearch(source: LyricsSource, query: String): List<LyricsCandidate>? {
        val stored = read(cachePath("search-${source.name.lowercase()}", query))
            ?.let { runCatching { json.decodeFromString<CachedSearch>(it) }.getOrNull() }
            ?: return null
        return stored.candidates.takeIf {
            stored.modelVersion == CACHE_MODEL_VERSION && !expired(stored.savedAtEpochMs, searchTtl)
        }
    }

    override fun putSearch(source: LyricsSource, query: String, candidates: List<LyricsCandidate>) {
        write(
            cachePath("search-${source.name.lowercase()}", query),
            json.encodeToString(CachedSearch(candidates, clock.millis())),
        )
    }

    override fun hasRecentMiss(trackKey: String): Boolean {
        val stored = read(cachePath("miss", trackKey)) ?: return false
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2 || parts[0].toIntOrNull() != CACHE_MODEL_VERSION) return false
        val timestamp = parts[1].toLongOrNull() ?: return false
        return !expired(timestamp, missTtl)
    }

    override fun putMiss(trackKey: String) = write(cachePath("miss", trackKey), "$CACHE_MODEL_VERSION:${clock.millis()}")

    override fun clearMiss(trackKey: String) {
        Files.deleteIfExists(cachePath("miss", trackKey))
    }

    override fun getOverride(trackKey: String): ManualOverride? = read(cachePath("override", trackKey))
        ?.let { runCatching { json.decodeFromString<ManualOverride>(it) }.getOrNull() }
        ?.takeIf { it.modelVersion == CACHE_MODEL_VERSION }

    override fun putOverride(trackKey: String, override: ManualOverride) {
        write(cachePath("override", trackKey), json.encodeToString(override))
        clearMiss(trackKey)
        synchronized(this) { memory.remove(trackKey) }
    }

    private fun validLyrics(value: CachedLyrics): Boolean =
        value.modelVersion == CACHE_MODEL_VERSION && value.encoderVersion == SpwLyricsEncoder.VERSION &&
            !expired(value.savedAtEpochMs, successTtl)

    private fun expired(epochMs: Long, ttl: Duration): Boolean =
        Instant.ofEpochMilli(epochMs).plus(ttl).isBefore(clock.instant())

    private fun cachePath(kind: String, rawKey: String): Path = root.resolve("$kind-${safeKey(rawKey)}.json")

    private fun safeKey(raw: String): String {
        if (raw.matches(Regex("[a-f0-9]{64}"))) return raw
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

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
