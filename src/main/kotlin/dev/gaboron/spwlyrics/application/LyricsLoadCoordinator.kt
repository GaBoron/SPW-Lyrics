package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
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
    private val translationInFlight = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val qualityUpgradeAttempts = BackgroundAttemptGate(QUALITY_UPGRADE_RETRY_SECONDS)
    private val translationAttempts = BackgroundAttemptGate(TRANSLATION_RETRY_SECONDS)
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
            if (!activeAutomaticLoads.contains(query.key)) {
                startBackgroundEnhancements(
                    query,
                    resolvedFromCache(query, cached),
                    automatic = override?.candidate == null,
                )
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
        startBackgroundEnhancements(query, resolved, automatic = false, force = true)
        return true
    }

    fun useLocal(): Boolean {
        val query = current.get() ?: return false
        cache.putOverride(query, ManualOverride(local = true))
        inFlight.remove(query.key)?.cancel(true)
        qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        translationInFlight.remove(query.key)?.cancel(true)
        notifiedFailures.remove(query.key)
        refreshOrNotify(query)
        return true
    }

    fun useAutomatic(): Boolean {
        val query = current.get() ?: return false
        inFlight.remove(query.key)?.cancel(true)
        qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        translationInFlight.remove(query.key)?.cancel(true)
        qualityUpgradeAttempts.clear(query.key)
        translationAttempts.clear(query.key)
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

    fun disableSupplementalTranslation(): Boolean {
        val query = current.get() ?: return false
        translationInFlight.remove(query.key)?.cancel(true)
        val cached = cache.getLyrics(query) ?: return false
        if (!SupplementalTranslationFallback.isAvailable(cached.document)) return false
        val override = cache.getOverride(query)
        if (override?.local == true) return false
        cache.putOverride(
            query,
            (override ?: ManualOverride(local = false)).copy(suppressSupplementalTranslation = true),
        )
        val document = SupplementalTranslationFallback.apply(cached.document)
        cache.putLyrics(query, cached.copy(document = document, encoded = SpwLyricsEncoder.encode(document)))
        notifiedFailures.remove(query.key)
        refreshOrNotify(query)
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
        val resolved = applyTranslationPreference(query, fetched)
        cache.putLyrics(query, resolver.toCache(resolved))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) refreshOrNotify(query)
        startBackgroundEnhancements(query, resolved, automatic = false, force = true)
    }

    private fun resolveAutomaticProgressively(query: TrackQuery, snapshotDeadlineNanos: Long) {
        var interimBase: ResolvedLyrics? = null
        var interimEnriched: ResolvedLyrics? = null
        val final = resolver.resolveAutomaticProgressively(query, snapshotDeadlineNanos) { interim ->
            applyAutomaticResult(query, interim)
            if (!automaticWasSuperseded(query)) {
                interimBase = interim
                val enriched = enrichTranslationFullyUnlessSuppressed(query, interim)
                interimEnriched = enriched
                applyAutomaticResult(query, enriched)
            }
        }
        if (final == null) {
            recordAutomaticFailure(query)
            return
        }
        if (automaticWasSuperseded(query)) return
        val enriched = if (final == interimBase) interimEnriched ?: final
        else enrichTranslationFullyUnlessSuppressed(query, final)
        applyAutomaticResult(query, enriched)
    }

    private fun applyAutomaticResult(query: TrackQuery, result: ResolvedLyrics) {
        if (automaticWasSuperseded(query)) return
        val resolved = applyTranslationPreference(query, result)
        val cached = cache.getLyrics(query)
        if (cached != null && !LyricsSelectionPolicy.canReplace(cached.document, resolved.document)) return
        if (cached?.encoded == resolved.encoded) {
            if (cached.document != resolved.document) cache.putLyrics(query, resolver.toCache(resolved))
            return
        }
        cache.putLyrics(query, resolver.toCache(resolved))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun startBackgroundEnhancements(
        query: TrackQuery,
        resolved: ResolvedLyrics,
        automatic: Boolean,
        force: Boolean = false,
    ) {
        if (automatic && LyricsQualityUpgradePolicy.shouldUpgrade(resolved.document)) {
            startQualityUpgrade(query, resolved, force)
        } else {
            startTranslationEnrichment(query, resolved, automatic, force)
        }
    }

    private fun startQualityUpgrade(query: TrackQuery, resolved: ResolvedLyrics, force: Boolean) {
        if (force) qualityUpgradeInFlight.remove(query.key)?.cancel(true)
        if (qualityUpgradeInFlight.containsKey(query.key) || !qualityUpgradeAttempts.allow(query.key, force)) return
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
        if (!sameBaseLyrics(cached.document, fallback.document)) return
        if (upgraded == null || !LyricsSelectionPolicy.canReplace(cached.document, upgraded.document)) {
            startTranslationEnrichment(query, resolvedFromCache(query, cached), automatic = true, force = true)
            return
        }
        val enriched = enrichTranslationFullyUnlessSuppressed(query, upgraded)
        applyAutomaticResult(query, enriched)
    }

    private fun enrichTranslationFullyUnlessSuppressed(query: TrackQuery, resolved: ResolvedLyrics): ResolvedLyrics =
        if (cache.getOverride(query)?.suppressSupplementalTranslation == true) resolved
        else resolver.enrichTranslationFully(resolved, query)

    private fun startTranslationEnrichment(
        query: TrackQuery,
        resolved: ResolvedLyrics,
        automatic: Boolean,
        force: Boolean = false,
    ) {
        if (!resolver.needsTranslationEnrichment(resolved)) return
        if (cache.getOverride(query)?.suppressSupplementalTranslation == true) return
        if (force) translationInFlight.remove(query.key)?.cancel(true)
        if (translationInFlight.containsKey(query.key) || !translationAttempts.allow(query.key, force)) return
        val future = CompletableFuture.runAsync({ enrichAndRefresh(query, resolved, automatic) }, executor)
        val previous = translationInFlight.putIfAbsent(query.key, future)
        if (previous != null) {
            future.cancel(true)
            return
        }
        future.whenComplete { _, _ -> translationInFlight.remove(query.key, future) }
    }

    private fun enrichAndRefresh(query: TrackQuery, resolved: ResolvedLyrics, automatic: Boolean) {
        if (automatic && automaticWasSuperseded(query)) return
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TRANSLATION_RESOLUTION_TIMEOUT_SECONDS)
        val enriched = applyTranslationPreference(query, resolver.enrichTranslation(resolved, query, deadline))
        if (enriched == resolved || automatic && automaticWasSuperseded(query)) return
        val cached = cache.getLyrics(query) ?: return
        if (!sameBaseLyrics(cached.document, resolved.document)) return
        cache.putLyrics(query, resolver.toCache(enriched))
        if (current.get()?.key == query.key) refreshOrNotify(query)
    }

    private fun applyTranslationPreference(query: TrackQuery, resolved: ResolvedLyrics): ResolvedLyrics {
        if (cache.getOverride(query)?.suppressSupplementalTranslation != true) return resolved
        val document = SupplementalTranslationFallback.apply(resolved.document)
        return if (document == resolved.document) resolved else {
            resolved.copy(document = document, encoded = SpwLyricsEncoder.encode(document))
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

    private fun sameBaseLyrics(left: LyricsDocument, right: LyricsDocument): Boolean =
        SupplementalTranslationFallback.apply(left) == SupplementalTranslationFallback.apply(right)

    private fun recordAutomaticFailure(query: TrackQuery) {
        if (automaticWasSuperseded(query)) return
        notifyAutomaticFailure(query)
    }

    private fun automaticWasSuperseded(query: TrackQuery): Boolean =
        cache.getOverride(query)?.let { it.local || it.candidate != null } == true

    private fun notifyAutomaticFailure(query: TrackQuery) {
        if (current.get()?.key == query.key && notifiedFailures.add(query.key)) {
            notify("自动加载歌词失败；可在插件设置中打开手动搜索，或启用快捷键后在 SPW 前台按 Ctrl+Shift+M。")
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
        translationInFlight.values.forEach { it.cancel(true) }
        executor.shutdownNow()
        resolver.close()
    }

    private companion object {
        const val INITIAL_RESOLUTION_SECONDS = 10L
        const val QUALITY_UPGRADE_RETRY_SECONDS = 60L
        const val TRANSLATION_RESOLUTION_TIMEOUT_SECONDS = 20L
        const val TRANSLATION_RETRY_SECONDS = 30L
    }
}
