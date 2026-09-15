package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.LyricsCache
import dev.gaboron.spwlyrics.storage.SpwLibraryCatalog
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal enum class LyricsBatchState { IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED }
internal enum class LyricsBatchItemState { WAITING, SEARCHING, COMPLETED, FAILED, CACHED, CANCELLED }

internal data class LyricsBatchItem(
    val query: TrackQuery,
    val state: LyricsBatchItemState,
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

    fun start(includeCached: Boolean, retryFailed: Boolean = false): LyricsBatchSnapshot = synchronized(lock) {
        if (state == LyricsBatchState.RUNNING || state == LyricsBatchState.PAUSED) return snapshotLocked()
        if (retryFailed && items.isNotEmpty()) {
            items = items.map { item ->
                if (item.state == LyricsBatchItemState.FAILED) item.copy(state = LyricsBatchItemState.WAITING, message = "")
                else item
            }.toMutableList()
        } else {
            prepare(includeCached)
        }
        if (items.none { it.state == LyricsBatchItemState.WAITING }) {
            state = LyricsBatchState.COMPLETED
            return snapshotLocked()
        }
        generation++
        state = LyricsBatchState.RUNNING
        nextIndex = AtomicInteger()
        remainingWorkers = AtomicInteger(PARALLEL_TRACKS)
        repeat(PARALLEL_TRACKS) { workers.execute { process(generation) } }
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
                    item.copy(state = LyricsBatchItemState.CANCELLED, message = "已停止")
                } else item
            }.toMutableList()
            lock.notifyAll()
        }
        snapshotLocked()
    }

    private fun prepare(includeCached: Boolean) {
        items = catalog.load().map { query ->
            if (!includeCached && cache.getLyrics(query) != null) {
                LyricsBatchItem(query, LyricsBatchItemState.CACHED, message = "已有缓存")
            } else {
                LyricsBatchItem(query, LyricsBatchItemState.WAITING)
            }
        }.toMutableList()
        state = LyricsBatchState.IDLE
    }

    private fun process(expectedGeneration: Int) {
        while (true) {
            val index = takeNext(expectedGeneration) ?: break
            val query = synchronized(lock) { items[index].query }
            val outcome = runCatching {
                val resolved = resolver.resolveAutomaticFully(query) ?: return@runCatching null
                resolver.enrichTranslationFully(resolved, query)
            }
            synchronized(lock) {
                if (generation == expectedGeneration) {
                    val resolved = outcome.getOrNull()
                    items[index] = when {
                        resolved != null -> {
                            cache.putLyrics(query, resolver.toCache(resolved))
                            LyricsBatchItem(
                                query = query,
                                state = LyricsBatchItemState.COMPLETED,
                                source = resolved.document.source.displayName,
                                quality = qualityLabel(resolved),
                                message = "已缓存",
                            )
                        }
                        else -> LyricsBatchItem(
                            query,
                            LyricsBatchItemState.FAILED,
                            message = outcome.exceptionOrNull()?.message?.take(80) ?: "未找到可靠歌词",
                        )
                    }
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
                items[index] = items[index].copy(state = LyricsBatchItemState.SEARCHING, message = "正在搜索")
                return@synchronized index
            }
            null
        }
    }

    private fun snapshotLocked() = LyricsBatchSnapshot(state, items.toList())

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
        const val PARALLEL_TRACKS = 2
    }
}
