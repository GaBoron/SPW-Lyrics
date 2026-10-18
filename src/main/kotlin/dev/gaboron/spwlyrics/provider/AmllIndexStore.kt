package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
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
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class AmllIndexStore(
    private val cacheDirectory: Path,
    private val http: ProviderHttp,
    private val clock: Clock,
    private val legacyIndexPath: Path? = null,
) : AutoCloseable {
    private val indexPath = cacheDirectory.resolve(INDEX_FILE_NAME)
    @Volatile private var searchIndex: AmllSearchIndex? = null
    private var lastRefreshAttempt: Instant? = null
    private var refreshTask: Future<*>? = null
    @Volatile private var closed = false
    private val refreshExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "spw-amll-index-refresh").apply { isDaemon = true }
    }

    fun search(query: TrackQuery, keywords: String, limit: Int, manual: Boolean = false): List<LyricsCandidate> {
        ensureCurrent()
        return searchIndex?.search(query, keywords, limit, manual).orEmpty()
    }

    @Synchronized
    private fun ensureCurrent() {
        if (closed) return
        Files.createDirectories(cacheDirectory)
        migrateLegacyIndex()
        if (searchIndex == null) loadIndex()
        val now = clock.instant()
        if (!isStale(now) || !mayAttemptRefresh(now) || refreshTask?.isDone == false) return
        lastRefreshAttempt = now
        if (searchIndex?.isEmpty == false) {
            // Keep searches responsive while an existing index is refreshed.
            refreshTask = refreshExecutor.submit {
                if (refresh(now)) synchronized(this) { if (!closed) loadIndex() }
            }
        } else if (refresh(now)) {
            loadIndex()
        }
    }

    private fun isStale(now: Instant): Boolean = !Files.isRegularFile(indexPath) ||
        !Files.getLastModifiedTime(indexPath).toInstant().plus(INDEX_TTL).isAfter(now)

    private fun mayAttemptRefresh(now: Instant): Boolean = lastRefreshAttempt
        ?.plus(REFRESH_RETRY_DELAY)
        ?.isAfter(now) != true

    private fun refresh(now: Instant): Boolean {
        return runCatching {
            val content = http.get(AmllProvider.INDEX_URL)
            check(!closed && !Thread.currentThread().isInterrupted) { "AMLL index refresh cancelled" }
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
        searchIndex = AmllSearchIndex(loaded)
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

    @Synchronized
    override fun close() {
        closed = true
        refreshTask?.cancel(true)
        refreshExecutor.shutdownNow()
    }

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
            titles = metadata["musicName"].orEmpty().filter(String::isNotBlank).ifEmpty { return@runCatching null },
            artists = metadata["artists"].orEmpty(),
            albums = metadata["album"].orEmpty(),
            ids = metadata.filterKeys { it.endsWith("Id", true) },
            rawLyricFile = root.string("rawLyricFile"),
        )
    }.getOrNull()

    private companion object {
        const val INDEX_FILE_NAME = "index.jsonl"
        val INDEX_TTL: Duration = Duration.ofHours(6)
        val REFRESH_RETRY_DELAY: Duration = Duration.ofMinutes(5)
    }
}
