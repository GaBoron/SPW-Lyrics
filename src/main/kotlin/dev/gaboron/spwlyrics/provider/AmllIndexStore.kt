package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.ProviderSearchPhase
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import java.awt.Desktop
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
    private var refreshing = false
    private var refreshFailure: Throwable? = null
    private val refreshListeners = mutableListOf<(ProviderSearchStatus) -> Unit>()
    @Volatile private var closed = false
    private val refreshExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "spw-amll-index-refresh").apply { isDaemon = true }
    }

    fun search(query: TrackQuery, keywords: String, limit: Int, manual: Boolean = false): List<LyricsCandidate> {
        ensureCurrent()
        return searchIndex?.search(query, keywords, limit, manual).orEmpty()
    }

    @Synchronized
    fun searchManual(query: TrackQuery, keywords: String, limit: Int,
        onStatus: (ProviderSearchStatus) -> Unit): List<LyricsCandidate> {
        val previousAttempt = lastRefreshAttempt
        ensureCurrent()
        val status = when {
            refreshing -> ProviderSearchStatus(LyricsSource.AMLL, ProviderSearchPhase.SEARCHING, "正在更新索引（使用本地索引）。")
            refreshFailure != null -> indexFailure(checkNotNull(refreshFailure))
            lastRefreshAttempt != previousAttempt -> ProviderSearchStatus(LyricsSource.AMLL, ProviderSearchPhase.AVAILABLE, "索引获取成功。")
            searchIndex?.isEmpty == false -> ProviderSearchStatus(LyricsSource.AMLL, ProviderSearchPhase.AVAILABLE, "本地索引可用；本次未检查网络。")
            else -> ProviderSearchStatus(LyricsSource.AMLL, ProviderSearchPhase.FAILED, "索引获取失败，请稍后重试。")
        }
        onStatus(status)
        if (refreshing) refreshListeners += onStatus
        return searchIndex?.search(query, keywords, limit, manual = true).orEmpty()
    }

    private fun indexFailure(error: Throwable): ProviderSearchStatus {
        val failure = ProviderSearchFailure.status(LyricsSource.AMLL, error)
        val reason = if (searchIndex?.isEmpty == false) "更新失败（使用本地索引）" else "索引获取失败"
        return failure.copy(detail = "$reason：${failure.detail}")
    }

    @Synchronized
    private fun ensureCurrent() {
        if (closed) return
        Files.createDirectories(cacheDirectory)
        if (searchIndex == null) loadIndex()
        val now = clock.instant()
        if ((searchIndex?.isEmpty == false && !isStale(now)) || !mayAttemptRefresh(now) || refreshing) return
        lastRefreshAttempt = now
        if (searchIndex?.isEmpty == false && Files.isRegularFile(indexPath)) {
            // Keep searches responsive while an existing index is refreshed.
            refreshing = true
            refreshTask = refreshExecutor.submit {
                val refreshed = runCatching { refresh(now).getOrThrow(); synchronized(this) { if (!closed) loadIndex() } }
                val (listeners, status) = synchronized(this) {
                    refreshing = false
                    refreshFailure = refreshed.exceptionOrNull()
                    val status = refreshFailure?.let(::indexFailure)
                        ?: ProviderSearchStatus(LyricsSource.AMLL, ProviderSearchPhase.AVAILABLE, "索引更新成功。")
                    refreshListeners.toList().also { refreshListeners.clear() } to status
                }
                if (!closed) listeners.forEach { runCatching { it(status) } }
            }
        } else {
            // Fetch the full index on first use, retaining the old subset if the upgrade fails.
            val refreshed = runCatching { refresh(now).getOrThrow(); loadIndex() }
            refreshFailure = refreshed.exceptionOrNull()
        }
    }

    private fun isStale(now: Instant): Boolean = !Files.isRegularFile(indexPath) ||
        !Files.getLastModifiedTime(indexPath).toInstant().plus(INDEX_TTL).isAfter(now)

    private fun mayAttemptRefresh(now: Instant): Boolean = lastRefreshAttempt
        ?.plus(REFRESH_RETRY_DELAY)
        ?.isAfter(now) != true

    private fun refresh(now: Instant): Result<Unit> {
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
                if (Files.exists(temporary)) {
                    val recycled = runCatching { Desktop.isDesktopSupported() &&
                        Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                        Desktop.getDesktop().moveToTrash(temporary.toFile()) }.getOrDefault(false)
                    if (!recycled) System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "AMLL 临时索引保留在：$temporary")
                }
            }
        }
    }

    private fun loadIndex() {
        val available = listOfNotNull(indexPath, cacheDirectory.resolve("index.jsonl"), legacyIndexPath)
            .firstOrNull(Files::isRegularFile)
        val loaded = if (available != null) {
            Files.newBufferedReader(available, StandardCharsets.UTF_8).use { reader ->
                parseRecords(reader.lineSequence())
            }
        } else {
            emptyList()
        }
        searchIndex = AmllSearchIndex(loaded)
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
        refreshListeners.clear()
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
        val rawLyricFile = root.string("rawLyricFile")?.takeIf(String::isNotBlank)
        val id = rawLyricFile ?: root.string("id")?.takeIf(String::isNotBlank) ?: return@runCatching null
        AmllRecord(
            id = id,
            titles = metadata["musicName"].orEmpty().filter(String::isNotBlank).ifEmpty { return@runCatching null },
            artists = metadata["artists"].orEmpty(),
            albums = metadata["album"].orEmpty(),
            ids = metadata.filterKeys { it.endsWith("Id", true) },
            rawLyricFile = rawLyricFile,
        )
    }.getOrNull()

    private companion object {
        const val INDEX_FILE_NAME = "raw-lyrics-index.jsonl"
        val INDEX_TTL: Duration = Duration.ofHours(6)
        val REFRESH_RETRY_DELAY: Duration = Duration.ofMinutes(5)
    }
}
