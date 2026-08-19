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
    private val notifiedFailures = ConcurrentHashMap.newKeySet<String>()

    fun currentQuery(): TrackQuery? = current.get()

    fun onLoad(
        query: TrackQuery,
        phase: LyricsLoadPhase,
        replacementPolicy: AutomaticReplacementPolicy,
    ): String? {
        current.set(query)
        val override = cache.getOverride(query)
        if (override?.local == true) return null
        val automaticLoadAllowed = replacementPolicy.allowsAutomaticLoad(phase)
        if (override?.candidate == null && !automaticLoadAllowed) return null
        cache.getLyrics(query)?.let {
            notifiedFailures.remove(query.key)
            return it.encoded
        }
        inFlight.computeIfAbsent(query.key) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AUTOMATIC_RESOLUTION_TIMEOUT_SECONDS)
            CompletableFuture.runAsync({ resolveAndRefresh(query, override?.candidate, deadline) }, executor)
                .orTimeout(AUTOMATIC_RESOLUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete { _, error ->
                    if (error != null && override?.candidate == null) recordAutomaticFailure(query)
                    inFlight.remove(query.key)
                }
        }
        return null
    }

    fun searchManual(keywords: String, source: dev.gaboron.spwlyrics.domain.LyricsSource?) =
        current.get()?.let { resolver.searchManual(it, keywords, source) }.orEmpty()

    fun preview(candidate: LyricsCandidate): ResolvedLyrics? = resolver.fetchManual(candidate)

    fun applyManual(candidate: LyricsCandidate): Boolean {
        val query = current.get() ?: return false
        val resolved = resolver.fetchManual(candidate) ?: return false
        cache.putOverride(query, ManualOverride(local = false, source = candidate.source, candidate = candidate))
        cache.putLyrics(query, resolver.toCache(resolved))
        notifiedFailures.remove(query.key)
        refreshOrNotify(query)
        return true
    }

    fun useLocal(): Boolean {
        val query = current.get() ?: return false
        cache.putOverride(query, ManualOverride(local = true))
        refreshOrNotify(query)
        return true
    }

    fun useAutomatic(): Boolean {
        val query = current.get() ?: return false
        inFlight.remove(query.key)?.cancel(true)
        cache.removeOverride(query)
        cache.removeLyrics(query)
        notifiedFailures.remove(query.key)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AUTOMATIC_RESOLUTION_TIMEOUT_SECONDS)
        val future = CompletableFuture.runAsync({ resolveAndRefresh(query, null, deadline) }, executor)
            .orTimeout(AUTOMATIC_RESOLUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        inFlight[query.key] = future
        future.whenComplete { _, error ->
            if (error != null) recordAutomaticFailure(query)
            inFlight.remove(query.key, future)
        }
        return true
    }

    private fun resolveAndRefresh(query: TrackQuery, manual: LyricsCandidate?, deadlineNanos: Long) {
        val resolved = if (manual == null) resolver.resolveAutomatic(query, deadlineNanos) else resolver.fetchManual(manual)
        if (manual == null && automaticWasSuperseded(query)) return
        if (resolved == null || System.nanoTime() >= deadlineNanos) {
            if (manual == null) recordAutomaticFailure(query)
            return
        }
        cache.putLyrics(query, resolver.toCache(resolved))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun recordAutomaticFailure(query: TrackQuery) {
        if (automaticWasSuperseded(query)) return
        notifyAutomaticFailure(query)
    }

    private fun automaticWasSuperseded(query: TrackQuery): Boolean =
        cache.getOverride(query)?.let { it.local || it.candidate != null } == true

    private fun notifyAutomaticFailure(query: TrackQuery) {
        if (current.get()?.key == query.key && notifiedFailures.add(query.key)) {
            notify("自动加载歌词失败；请确保设置中的“通过快捷键开启”已打开，然后在 SPW 前台按 Ctrl+Shift+M 手动匹配。")
        }
    }

    private fun refreshOrNotify(query: TrackQuery) {
        if (current.get()?.key != query.key) return
        if (!refreshBridge.reloadCurrentLyrics()) notify("歌词已缓存；当前 SPW 版本无法自动刷新，请重新选曲。")
    }

    override fun close() {
        current.set(null)
        inFlight.values.forEach { it.cancel(true) }
        executor.shutdownNow()
    }

    private companion object {
        const val AUTOMATIC_RESOLUTION_TIMEOUT_SECONDS = 15L
    }
}
