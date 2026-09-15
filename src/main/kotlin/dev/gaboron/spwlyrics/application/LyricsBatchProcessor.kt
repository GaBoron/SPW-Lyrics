package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.LyricsCache
import dev.gaboron.spwlyrics.storage.SpwLibraryCatalog
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal enum class LyricsBatchState { IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED }
internal enum class LyricsBatchItemState { WAITING, SEARCHING, COMPLETED, FAILED, CACHED, EXCLUDED, CANCELLED }

internal data class LyricsBatchItem(
    val query: TrackQuery,
    val state: LyricsBatchItemState,
    val selected: Boolean = true,
    val progress: Double = 0.0,
    val stage: String = "等待处理",
    val source: String = "",
    val quality: String = "",
    val message: String = "",
)

internal data class LyricsBatchSnapshot(
    val state: LyricsBatchState,
    val items: List<LyricsBatchItem>,
)

/** Preloads reliable lyrics into the plugin cache while keeping playback refresh work separate. */
internal class LyricsBatchProcessor(
    private val catalog: SpwLibraryCatalog,
    private val cache: LyricsCache,
    private val resolver: LyricsResolver,
) : AutoCloseable {
    private val workers = Executors.newFixedThreadPool(PARALLEL_TRACKS) { task ->
        Thread(task, "spw-lyrics-batch").apply { isDaemon = true }
    }
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private var state = LyricsBatchState.IDLE
    private var items = mutableListOf<LyricsBatchItem>()
    private var generation = 0
    private var nextIndex = AtomicInteger()
    private var remainingWorkers = AtomicInteger()

    fun snapshot(loadLibrary: Boolean = false): LyricsBatchSnapshot = synchronized(lock) {
        if (loadLibrary && items.isEmpty() && state == LyricsBatchState.IDLE) prepare(includeCached = false)
        snapshotLocked()
    }

    fun start(
        includeCached: Boolean,
        selectedKeys: Set<String>? = null,
        retryFailed: Boolean = false,
    ): LyricsBatchSnapshot = synchronized(lock) {
        if (state == LyricsBatchState.RUNNING || state == LyricsBatchState.PAUSED) return snapshotLocked()
        if (retryFailed && items.isNotEmpty()) {
            items = items.map { item ->
                if (item.state == LyricsBatchItemState.FAILED) item.copy(
                    state = LyricsBatchItemState.WAITING,
                    progress = 0.0,
                    stage = "等待重试",
                    message = "",
                )
                else item
            }.toMutableList()
        } else {
            prepare(includeCached, selectedKeys)
        }
        if (items.none { it.state == LyricsBatchItemState.WAITING }) {
            state = LyricsBatchState.COMPLETED
            return snapshotLocked()
        }
        generation++
        state = LyricsBatchState.RUNNING
        nextIndex = AtomicInteger()
        remainingWorkers = AtomicInteger(PARALLEL_TRACKS)
        val sharedResolutions = ConcurrentHashMap<String, CompletableFuture<ResolvedLyrics?>>()
        repeat(PARALLEL_TRACKS) { workers.execute { process(generation, sharedResolutions) } }
        snapshotLocked()
    }

    fun pause(): LyricsBatchSnapshot = synchronized(lock) {
        if (state == LyricsBatchState.RUNNING) state = LyricsBatchState.PAUSED
        snapshotLocked()
    }

    fun resume(): LyricsBatchSnapshot = synchronized(lock) {
        if (state == LyricsBatchState.PAUSED) {
            state = LyricsBatchState.RUNNING
            lock.notifyAll()
        }
        snapshotLocked()
    }

    fun cancel(): LyricsBatchSnapshot = synchronized(lock) {
        if (state == LyricsBatchState.RUNNING || state == LyricsBatchState.PAUSED) {
            generation++
            state = LyricsBatchState.CANCELLED
            items = items.map { item ->
                if (item.state == LyricsBatchItemState.WAITING || item.state == LyricsBatchItemState.SEARCHING) {
                    item.copy(state = LyricsBatchItemState.CANCELLED, stage = "已停止", message = "已停止")
                } else item
            }.toMutableList()
            lock.notifyAll()
        }
        snapshotLocked()
    }

    private fun prepare(includeCached: Boolean, selectedKeys: Set<String>? = null) {
        items = catalog.load().map { query ->
            when {
                selectedKeys != null && query.key !in selectedKeys -> LyricsBatchItem(
                    query,
                    LyricsBatchItemState.EXCLUDED,
                    selected = false,
                    stage = "未选择",
                    message = "未选择",
                )
                !includeCached && cache.getLyrics(query) != null -> LyricsBatchItem(
                    query,
                    LyricsBatchItemState.CACHED,
                    progress = 1.0,
                    stage = "已有缓存",
                    message = "已有缓存",
                )
                else -> LyricsBatchItem(query, LyricsBatchItemState.WAITING)
            }
        }.toMutableList()
        state = LyricsBatchState.IDLE
    }

    private fun process(
        expectedGeneration: Int,
        sharedResolutions: ConcurrentHashMap<String, CompletableFuture<ResolvedLyrics?>>,
    ) {
        while (true) {
            val index = takeNext(expectedGeneration) ?: break
            val query = synchronized(lock) { items[index].query }
            val outcome = runCatching { resolveShared(query, expectedGeneration, index, sharedResolutions) }
            val resolved = outcome.getOrNull()
            if (resolved != null && isCurrent(expectedGeneration)) {
                updateProgress(expectedGeneration, index, 0.97, "正在写入缓存", "正在写入缓存")
            }
            val cacheFailure = if (resolved != null && isCurrent(expectedGeneration)) {
                runCatching { cache.putLyrics(query, resolver.toCache(resolved)) }.exceptionOrNull()
            } else null
            synchronized(lock) {
                if (generation != expectedGeneration) return@synchronized
                items[index] = if (resolved != null && cacheFailure == null) {
                    LyricsBatchItem(
                        query = query,
                        state = LyricsBatchItemState.COMPLETED,
                        progress = 1.0,
                        stage = "处理完成",
                        source = resolved.document.source.displayName,
                        quality = qualityLabel(resolved),
                        message = "已缓存",
                    )
                } else {
                    LyricsBatchItem(
                        query,
                        LyricsBatchItemState.FAILED,
                        progress = 1.0,
                        stage = "处理失败",
                        message = (cacheFailure ?: outcome.exceptionOrNull())?.message?.take(80) ?: "未找到可靠歌词",
                    )
                }
            }
        }
        synchronized(lock) {
            if (generation == expectedGeneration && remainingWorkers.decrementAndGet() == 0) {
                state = LyricsBatchState.COMPLETED
            }
        }
    }

    private fun takeNext(expectedGeneration: Int): Int? {
        return synchronized(lock) {
            while (generation == expectedGeneration && state == LyricsBatchState.PAUSED) lock.wait()
            if (generation != expectedGeneration || state != LyricsBatchState.RUNNING) return@synchronized null
            while (nextIndex.get() < items.size) {
                val index = nextIndex.getAndIncrement()
                if (index >= items.size) return@synchronized null
                if (items[index].state != LyricsBatchItemState.WAITING) continue
            items[index] = items[index].copy(
                state = LyricsBatchItemState.SEARCHING,
                progress = 0.02,
                stage = "准备搜索",
                message = "正在准备搜索",
            )
                return@synchronized index
            }
            null
        }
    }

    private fun snapshotLocked() = LyricsBatchSnapshot(state, items.toList())

    private fun resolveShared(
        query: TrackQuery,
        expectedGeneration: Int,
        index: Int,
        shared: ConcurrentHashMap<String, CompletableFuture<ResolvedLyrics?>>,
    ): ResolvedLyrics? {
        val own = CompletableFuture<ResolvedLyrics?>()
        val existing = shared.putIfAbsent(recordingKey(query), own)
        if (existing != null) {
            updateProgress(expectedGeneration, index, 0.08, "复用搜索结果", "等待相同曲目的搜索结果")
            return existing.get()
        }
        return try {
            val resolved = resolver.resolveAutomaticFully(query) { progress ->
                updateProgress(expectedGeneration, index, progress.fraction, stageLabel(progress.stage), progress.detail)
            }?.let { found ->
                resolver.enrichTranslationFully(found, query) { progress ->
                    updateProgress(expectedGeneration, index, progress.fraction, stageLabel(progress.stage), progress.detail)
                }
            }
            own.complete(resolved)
            resolved
        } catch (error: Throwable) {
            own.completeExceptionally(error)
            throw error
        }
    }

    private fun updateProgress(expectedGeneration: Int, index: Int, progress: Double, stage: String, detail: String) {
        synchronized(lock) {
            if (generation != expectedGeneration || items.getOrNull(index)?.state != LyricsBatchItemState.SEARCHING) return
            items[index] = items[index].copy(
                progress = progress.coerceIn(0.0, 1.0),
                stage = stage,
                message = detail,
            )
        }
    }

    private fun isCurrent(expectedGeneration: Int): Boolean = synchronized(lock) { generation == expectedGeneration }

    private fun stageLabel(stage: LyricsResolutionStage): String = when (stage) {
        LyricsResolutionStage.SEARCHING -> "搜索歌词"
        LyricsResolutionStage.SELECTING -> "选择最优歌词"
        LyricsResolutionStage.TRANSLATING -> "补充翻译"
    }

    private fun recordingKey(query: TrackQuery): String = listOf(
        query.title.trim().lowercase(),
        query.artists.joinToString("/") { it.trim().lowercase() },
        query.album.trim().lowercase(),
        query.durationMs?.div(2_000)?.toString().orEmpty(),
    ).joinToString("|")

    private fun qualityLabel(resolved: ResolvedLyrics): String = when (resolved.document.quality) {
        dev.gaboron.spwlyrics.domain.LyricsQuality.KARAOKE_SYNCED -> "逐字"
        dev.gaboron.spwlyrics.domain.LyricsQuality.LINE_SYNCED -> "逐行"
        dev.gaboron.spwlyrics.domain.LyricsQuality.PLAIN -> "普通"
    }

    override fun close() {
        synchronized(lock) {
            generation++
            state = LyricsBatchState.CANCELLED
            lock.notifyAll()
        }
        workers.shutdownNow()
    }

    private companion object {
        const val PARALLEL_TRACKS = 3
    }
}
