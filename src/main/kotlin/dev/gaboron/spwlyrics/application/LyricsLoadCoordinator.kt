package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.CachedLyrics
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
    private val refreshRetrier = LyricsRefreshRetrier(refreshBridge, notify)
    private val current = AtomicReference<TrackQuery?>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val activeAutomaticLoads = ConcurrentHashMap<String, Any>()
    private val qualityUpgradeInFlight = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val qualityUpgradeAttempts = BackgroundAttemptGate(QUALITY_UPGRADE_RETRY_SECONDS)
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
        cache.getLyrics(query)?.let { cached ->
            notifiedFailures.remove(query.key)
            if (override?.candidate == null && !activeAutomaticLoads.contains(query.key)) {
                startQualityUpgradeIfNeeded(query, resolvedFromCache(query, cached))
            }
            return cached.encoded
        }
        inFlight.computeIfAbsent(query.key) {
            val automaticToken = Any()
            if (override?.candidate == null) activeAutomaticLoads[query.key] = automaticToken
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INITIAL_RESOLUTION_SECONDS)
            val future = CompletableFuture.runAsync({ resolveAndRefresh(query, override?.candidate, deadline) }, executor)
            future.whenComplete { _, error ->
                if (error != null && !future.isCancelled && override?.candidate == null) recordAutomaticFailure(query)
                activeAutomaticLoads.remove(query.key, automaticToken)
                inFlight.remove(query.key, future)
            }
            future
        }
        return null
    }

    fun searchManual(keywords: String, source: dev.gaboron.spwlyrics.domain.LyricsSource?) =
        current.get()?.let { resolver.searchManual(it, keywords, source) }.orEmpty()

    fun preview(candidate: LyricsCandidate): ResolvedLyrics? = resolver.fetchManual(candidate)

    fun applyManual(candidate: LyricsCandidate): Boolean {
        val query = current.get() ?: return false
        qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        val resolved = resolver.fetchManual(candidate) ?: return false
        cache.putOverride(query, ManualOverride(local = false, source = candidate.source, candidate = candidate))
        inFlight.remove(query.key)?.cancel(true)
        cache.putLyrics(query, resolver.toCache(resolved))
        notifiedFailures.remove(query.key)
        refreshOrNotify(query)
        return true
    }

    fun useLocal(): Boolean {
        val query = current.get() ?: return false
        cache.putOverride(query, ManualOverride(local = true))
        inFlight.remove(query.key)?.cancel(true)
        qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        notifiedFailures.remove(query.key)
        refreshOrNotify(query)
        return true
    }

    fun useAutomatic(): Boolean {
        val query = current.get() ?: return false
        inFlight.remove(query.key)?.cancel(true)
        qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        qualityUpgradeAttempts.clear(query.key)
        cache.removeOverride(query)
        cache.removeLyrics(query)
        notifiedFailures.remove(query.key)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INITIAL_RESOLUTION_SECONDS)
        val automaticToken = Any()
        activeAutomaticLoads[query.key] = automaticToken
        val future = CompletableFuture.runAsync({ resolveAndRefresh(query, null, deadline) }, executor)
        inFlight[query.key] = future
        future.whenComplete { _, error ->
            if (error != null && !future.isCancelled) recordAutomaticFailure(query)
            activeAutomaticLoads.remove(query.key, automaticToken)
            inFlight.remove(query.key, future)
        }
        return true
    }

    private fun resolveAndRefresh(query: TrackQuery, manual: LyricsCandidate?, deadlineNanos: Long) {
        if (manual == null) {
            resolveAutomaticProgressively(query, deadlineNanos)
            return
        }
        val fetched = resolver.fetchManual(manual)
        if (fetched == null) {
            return
        }
        cache.putLyrics(query, resolver.toCache(fetched))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun resolveAutomaticProgressively(query: TrackQuery, snapshotDeadlineNanos: Long) {
        val final = resolver.resolveAutomaticProgressively(query, snapshotDeadlineNanos) { interim ->
            applyAutomaticResult(query, interim)
        }
        if (final == null) {
            recordAutomaticFailure(query)
            return
        }
        if (automaticWasSuperseded(query)) return
        applyAutomaticResult(query, final)
    }

    private fun applyAutomaticResult(query: TrackQuery, result: ResolvedLyrics) {
        if (automaticWasSuperseded(query)) return
        val cached = cache.getLyrics(query)
        if (cached != null && !LyricsSelectionPolicy.canReplace(cached.document, result.document)) return
        if (cached?.encoded == result.encoded) {
            if (cached.document != result.document) cache.putLyrics(query, resolver.toCache(result))
            return
        }
        cache.putLyrics(query, resolver.toCache(result))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun startQualityUpgradeIfNeeded(query: TrackQuery, resolved: ResolvedLyrics) {
        if (!LyricsQualityUpgradePolicy.shouldUpgrade(resolved.document)) return
        if (qualityUpgradeInFlight.containsKey(query.key) || !qualityUpgradeAttempts.allow(query.key, force = false)) return
        val future = CompletableFuture.runAsync({ upgradeQualityAndRefresh(query, resolved) }, executor)
        val previous = qualityUpgradeInFlight.putIfAbsent(query.key, future)
        if (previous != null) {
            future.cancel(true)
            return
        }
        future.whenComplete { _, _ -> qualityUpgradeInFlight.remove(query.key, future) }
    }

    private fun upgradeQualityAndRefresh(query: TrackQuery, fallback: ResolvedLyrics) {
        if (automaticWasSuperseded(query)) return
        val upgraded = resolver.resolveAutomaticFully(query)
        if (automaticWasSuperseded(query)) return
        val cached = cache.getLyrics(query) ?: return
        if (cached.document != fallback.document) return
        if (upgraded != null && LyricsSelectionPolicy.canReplace(cached.document, upgraded.document)) {
            applyAutomaticResult(query, upgraded)
        }
    }

    private fun resolvedFromCache(query: TrackQuery, cached: CachedLyrics): ResolvedLyrics = ResolvedLyrics(
        candidate = LyricsCandidate(
            source = cached.document.source,
            remoteId = "",
            title = query.title,
            artists = query.artists,
            album = query.album,
            durationMs = query.durationMs,
            externalIds = query.externalIds,
        ),
        document = cached.document,
        encoded = cached.encoded,
    )

    private fun recordAutomaticFailure(query: TrackQuery) {
        if (automaticWasSuperseded(query)) return
        notifyAutomaticFailure(query)
    }

    private fun automaticWasSuperseded(query: TrackQuery): Boolean =
        cache.getOverride(query)?.let { it.local || it.candidate != null } == true

    private fun notifyAutomaticFailure(query: TrackQuery) {
        if (current.get()?.key == query.key && notifiedFailures.add(query.key)) {
            notify("自动加载歌词失败；可在插件设置中打开手动搜索，或使用 SPW 中配置的歌词搜索快捷键。")
        }
    }

    private fun refreshOrNotify(query: TrackQuery) {
        refreshRetrier.refresh { current.get()?.key == query.key }
    }

    override fun close() {
        current.set(null)
        activeAutomaticLoads.clear()
        inFlight.values.forEach { it.cancel(true) }
        qualityUpgradeInFlight.values.forEach { it.cancel(true) }
        executor.shutdownNow()
        resolver.close()
    }

    private companion object {
        const val INITIAL_RESOLUTION_SECONDS = 10L
        const val QUALITY_UPGRADE_RETRY_SECONDS = 60L
    }
}
