package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.LyricsCache
import dev.gaboron.spwlyrics.storage.ManualOverride
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

interface LyricsRefreshBridge {
    fun reloadCurrentLyrics(): Boolean
}

class LyricsLoadCoordinator(
    private val cache: LyricsCache,
    private val resolver: LyricsResolver,
    private val refreshBridge: LyricsRefreshBridge,
    private val notify: (String) -> Unit = {},
    private val executor: ExecutorService = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "spw-lyrics-worker").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val current = AtomicReference<TrackQuery?>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<*>>()

    fun currentQuery(): TrackQuery? = current.get()

    fun onBeforeLoad(query: TrackQuery): String? {
        current.set(query)
        val override = cache.getOverride(query.key)
        if (override?.local == true) return null
        cache.getLyrics(query.key)?.let { return it.encoded }
        if (cache.hasRecentMiss(query.key) && override?.candidate == null) return null

        inFlight.computeIfAbsent(query.key) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            CompletableFuture.runAsync({ resolveAndRefresh(query, override?.candidate, deadline) }, executor)
                .orTimeout(8, TimeUnit.SECONDS)
                .whenComplete { _, _ -> inFlight.remove(query.key) }
        }
        return null
    }

    fun searchManual(keywords: String, source: dev.gaboron.spwlyrics.domain.LyricsSource?) =
        current.get()?.let { resolver.searchManual(it, keywords, source) }.orEmpty()

    fun preview(candidate: LyricsCandidate): ResolvedLyrics? = resolver.fetchManual(candidate)

    fun applyManual(candidate: LyricsCandidate): Boolean {
        val query = current.get() ?: return false
        val resolved = resolver.fetchManual(candidate) ?: return false
        cache.putOverride(query.key, ManualOverride(local = false, source = candidate.source, candidate = candidate))
        cache.putLyrics(query.key, resolver.toCache(resolved))
        refreshOrNotify(query)
        return true
    }

    fun useLocal(): Boolean {
        val query = current.get() ?: return false
        cache.putOverride(query.key, ManualOverride(local = true))
        refreshOrNotify(query)
        return true
    }

    private fun resolveAndRefresh(query: TrackQuery, manual: LyricsCandidate?, deadlineNanos: Long) {
        val resolved = if (manual == null) resolver.resolveAutomatic(query, deadlineNanos) else resolver.fetchManual(manual)
        if (System.nanoTime() >= deadlineNanos) return
        if (resolved == null) {
            if (manual == null) cache.putMiss(query.key)
            return
        }
        cache.putLyrics(query.key, resolver.toCache(resolved))
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun refreshOrNotify(query: TrackQuery) {
        if (current.get()?.key != query.key) return
        if (!refreshBridge.reloadCurrentLyrics()) notify("歌词已缓存；当前 SPW 版本无法自动刷新，请重新选曲。")
    }

    override fun close() {
        inFlight.values.forEach { it.cancel(true) }
        executor.shutdownNow()
    }
}
