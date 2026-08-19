package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TextNormalizer
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class AmllIndexStore(
    private val cacheDirectory: Path,
    private val http: ProviderHttp,
    private val clock: Clock,
    private val legacyIndexPath: Path? = null,
) {
    private val indexPath = cacheDirectory.resolve(INDEX_FILE_NAME)
    @Volatile private var records: List<AmllRecord>? = null
    @Volatile private var inverted: Map<String, Set<Int>>? = null
    @Volatile private var byExternalId: Map<String, Set<Int>>? = null
    private var lastRefreshAttempt: Instant? = null

    fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        ensureCurrent()
        val all = records.orEmpty()
        if (all.isEmpty()) return emptyList()
        val exactIds = query.externalIds.values.flatMap { byExternalId.orEmpty()[it].orEmpty() }.distinct()
        if (exactIds.isNotEmpty()) return exactIds.mapNotNull(all::getOrNull).take(limit).map(AmllRecord::candidate)
        val tokens = TextNormalizer.normalize(keywords).split(' ').filter { it.length >= 2 }.toSet()
        val ids = tokens.flatMap { inverted.orEmpty()[it].orEmpty() }.distinct()
        val pool = if (ids.isNotEmpty()) ids.mapNotNull(all::getOrNull) else emptyList()
        return pool.asSequence()
            .sortedByDescending { record ->
                TextNormalizer.similarity(query.title, record.title) * 0.7 +
                    TextNormalizer.similarity(query.artists.joinToString(" "), record.artists.joinToString(" ")) * 0.3
            }
            .take(limit)
            .map(AmllRecord::candidate)
            .toList()
    }

    @Synchronized
    private fun ensureCurrent() {
        Files.createDirectories(cacheDirectory)
        migrateLegacyIndex()
        val now = clock.instant()
        val refreshed = isStale(now) && mayAttemptRefresh(now) && refresh(now)
        if (records == null || refreshed) loadIndex()
    }

    private fun isStale(now: Instant): Boolean = !Files.isRegularFile(indexPath) ||
        !Files.getLastModifiedTime(indexPath).toInstant().plus(INDEX_TTL).isAfter(now)

    private fun mayAttemptRefresh(now: Instant): Boolean = lastRefreshAttempt
        ?.plus(REFRESH_RETRY_DELAY)
        ?.isAfter(now) != true

    private fun refresh(now: Instant): Boolean {
        lastRefreshAttempt = now
        return runCatching {
            val content = http.get(AmllProvider.INDEX_URL)
            check(parseRecords(content.lineSequence()).isNotEmpty()) { "AMLL index contains no valid records" }
            val temporary = Files.createTempFile(cacheDirectory, "amll-index", ".tmp")
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8)
                Files.setLastModifiedTime(temporary, FileTime.from(now))
                moveReplacing(temporary, indexPath)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }.isSuccess
    }

    private fun loadIndex() {
        val loaded = if (Files.isRegularFile(indexPath)) {
            Files.newBufferedReader(indexPath, StandardCharsets.UTF_8).use { reader ->
                parseRecords(reader.lineSequence())
            }
        } else {
            emptyList()
        }
        records = loaded
        inverted = buildMap {
            loaded.forEachIndexed { index, record ->
                val tokens = TextNormalizer.normalize(
                    listOf(record.title, record.artists.joinToString(" "), record.album).joinToString(" "),
                ).split(' ').filter { it.length >= 2 }.toSet()
                tokens.forEach { token -> put(token, getOrDefault(token, emptySet()) + index) }
            }
        }
        byExternalId = buildMap {
            loaded.forEachIndexed { index, record ->
                record.ids.values.flatten().forEach { id -> put(id, getOrDefault(id, emptySet()) + index) }
            }
        }
    }

    private fun migrateLegacyIndex() {
        val legacy = legacyIndexPath?.takeIf(Files::isRegularFile) ?: return
        if (Files.exists(indexPath)) return
        runCatching { moveReplacing(legacy, indexPath) }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parseRecords(lines: Sequence<String>): List<AmllRecord> = lines.mapNotNull(::parseRecord).toList()

    private fun parseRecord(line: String): AmllRecord? = runCatching {
        val root = providerJson.parseToJsonElement(line) as JsonObject
        val metadata = (root["metadata"] as? JsonArray).orEmpty().associate { pairElement ->
            val pair = pairElement as JsonArray
            val key = (pair[0] as JsonPrimitive).content
            val values = (pair[1] as JsonArray).map { (it as JsonPrimitive).content }
            key to values
        }
        val id = root.string("id") ?: return@runCatching null
        AmllRecord(
            id = id,
            title = metadata["musicName"]?.firstOrNull().orEmpty(),
            artists = metadata["artists"].orEmpty(),
            album = metadata["album"]?.firstOrNull().orEmpty(),
            ids = metadata.filterKeys { it.endsWith("Id", true) },
        )
    }.getOrNull()

    private companion object {
        const val INDEX_FILE_NAME = "index.jsonl"
        val INDEX_TTL: Duration = Duration.ofDays(1)
        val REFRESH_RETRY_DELAY: Duration = Duration.ofHours(1)
    }
}

private data class AmllRecord(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val ids: Map<String, List<String>>,
) {
    fun candidate() = LyricsCandidate(
        source = LyricsSource.AMLL,
        remoteId = id,
        title = title,
        artists = artists,
        album = album,
        qualityHint = LyricsQuality.WORD_SYNCED,
        externalIds = ids.mapValues { it.value.firstOrNull().orEmpty() },
        context = mapOf("url" to "${AmllProvider.RAW_BASE}/$id.ttml"),
    )
}
