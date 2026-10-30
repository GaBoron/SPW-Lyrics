package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.CachedLyrics
import dev.gaboron.spwlyrics.storage.LyricsCache
import dev.gaboron.spwlyrics.storage.ManualOverride
import dev.gaboron.spwlyrics.storage.LyricsDelaySettings
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

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
    private val delays: LyricsDelaySettings? = null,
    private val output: LyricsOutputRouter? = null,
    private val prepareQuery: (TrackQuery) -> TrackQuery = { it },
    private val performance: PluginPerformance = PluginPerformance(),
) : AutoCloseable {
    private val refreshRetrier = LyricsRefreshRetrier(refreshBridge, notify)
    private val current = AtomicReference<TrackQuery?>()
    private val localLyrics = AtomicReference<Pair<String, LyricsDocument>?>()
    private val tasks = LyricsResolutionTasks(executor, performance = performance)
    private val qualityUpgradeAttempts = BackgroundAttemptGate(QUALITY_UPGRADE_RETRY_SECONDS)
    private val notifiedFailures = ConcurrentHashMap.newKeySet<String>()
    private val selectionLock = Any()
    @Volatile private var closed = false

    fun currentQuery(): TrackQuery? = current.get()

    fun selectionChanged() {
        if (closed) return
        synchronized(selectionLock) { tasks.cancelAll() }
        current.get()?.takeUnless(::automaticWasSuperseded)?.let { query ->
            output?.withdraw(query)
            refreshOrNotify(query)
        }
    }

    fun onLoad(
        query: TrackQuery,
        phase: LyricsLoadPhase,
        replacementPolicy: AutomaticReplacementPolicy,
        localTtml: (() -> LyricsDocument?)? = null,
    ): String? {
        synchronized(selectionLock) {
            if (closed) return null
            if (current.getAndSet(query)?.key != query.key) {
                tasks.cancelExcept(query.key, upgradeKey(query))
            }
        }
        val override = cache.getOverride(query)
        if (phase == LyricsLoadPhase.BEFORE_LOCAL) {
            val local = if (override?.candidate == null) localTtml?.invoke() else null
            localLyrics.set(local?.let { query.key to it })
            if (local != null) return render(query, local)
        } else if (override?.candidate == null) {
            // Reuse the local TTML document if SPW also invokes its after-missing hook.
            localLyrics.get()?.takeIf { it.first == query.key }?.let { return render(query, it.second) }
        }
        if (override?.local == true) return null
        val automaticLoadAllowed = replacementPolicy.allowsAutomaticLoad(phase)
        if (override?.candidate == null && !automaticLoadAllowed) return null
        cache.getLyrics(query)?.takeIf { override?.candidate != null || it.selectionKey == resolver.selectionKey() }?.let { cached ->
            notifiedFailures.remove(query.key)
            if (override?.candidate == null && !tasks.contains(query.key)) {
                startQualityUpgradeIfNeeded(query, resolvedFromCache(query, cached))
            }
            return render(query, cached.document, cached.encoded)
        }
        startLoad(query, override?.candidate)
        return null
    }

    /** Warms disk entries and shares an unfinished speculative search with later playback. */
    fun precache(query: TrackQuery, hasLocalLyrics: Boolean = false): Future<*>? {
        if (closed) return null
        val override = cache.getOverride(query)
        if (override?.local == true) return null
        if (override?.candidate == null && hasLocalLyrics) return null
        if (cache.getLyrics(query)?.let { override?.candidate != null || it.selectionKey == resolver.selectionKey() } == true) return null
        return startLoad(query, override?.candidate, background = true)
    }

    fun searchManual(query: TrackQuery, keywords: String, source: dev.gaboron.spwlyrics.domain.LyricsSource?,
        onStatus: (dev.gaboron.spwlyrics.domain.ProviderSearchStatus) -> Unit = {}) =
        resolver.searchManual(query, keywords, source, onStatus)

    fun preview(candidate: LyricsCandidate): ResolvedLyrics? = resolver.fetchManual(candidate)

    /** Previews automatic matching without applying lyrics or changing a manual/local override. */
    fun previewAutomatic(query: TrackQuery): ResolvedLyrics? {
        val selectionKey = resolver.selectionKey()
        val cached = cache.getLyrics(query)?.takeIf { it.selectionKey == selectionKey }
        val resolved = cached?.candidate?.let { ResolvedLyrics(it, cached.document, cached.selectionKey) }
            ?: resolver.resolveAutomaticFully(prepareQuery(query))
        return resolved?.takeIf { it.selectionKey == resolver.selectionKey() }?.also(resolver::rememberManual)
    }

    fun applyManual(query: TrackQuery, candidate: LyricsCandidate): Boolean {
        if (current.get()?.key != query.key) return false
        tasks.cancel(upgradeKey(query))
        val resolved = resolver.fetchManual(candidate) ?: return false
        val cached = resolver.toCache(resolved)
        synchronized(selectionLock) {
            if (current.get()?.key != query.key) return false
            tasks.cancel(query.key)
            tasks.cancel(upgradeKey(query))
            cache.putOverride(query, ManualOverride(local = false, source = candidate.source, candidate = candidate))
            cache.putLyrics(query, cached)
            offer(query, resolved.document)
            notifiedFailures.remove(query.key)
            refreshOrNotify(query)
        }
        return true
    }

    fun useLocal(query: TrackQuery): Boolean {
        if (current.get()?.key != query.key) return false
        synchronized(selectionLock) {
            if (current.get()?.key != query.key) return false
            tasks.cancel(query.key)
            tasks.cancel(upgradeKey(query))
            cache.putOverride(query, ManualOverride(local = true))
            notifiedFailures.remove(query.key)
            output?.withdraw(query)
            refreshOrNotify(query)
        }
        return true
    }

    fun useAutomatic(query: TrackQuery): Boolean {
        if (current.get()?.key != query.key) return false
        synchronized(selectionLock) {
            if (current.get()?.key != query.key) return false
            tasks.cancel(query.key)
            tasks.cancel(upgradeKey(query))
            qualityUpgradeAttempts.clear(query.key)
            cache.removeOverride(query)
            cache.removeLyrics(query)
            output?.withdraw(query)
            notifiedFailures.remove(query.key)
            startLoad(query, null)
        }
        return true
    }

    fun setDelay(query: TrackQuery, delayMs: Int): Boolean {
        if (current.get()?.key != query.key) return false
        val settings = delays ?: return false
        settings.set(query, delayMs)
        output?.setDelay(query, delayMs)
        refreshOrNotify(query)
        return true
    }

    private fun startLoad(query: TrackQuery, manual: LyricsCandidate?, background: Boolean = false): Future<*> =
        tasks.start(query.key, background) { isActive ->
            try {
                if (manual == null) {
                    val prepared = prepareQuery(query)
                    if (!isActive()) return@start
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INITIAL_RESOLUTION_SECONDS)
                    val final = resolver.resolveAutomaticProgressively(prepared, deadline, background) { interim ->
                        applyAutomaticResult(query, interim, isActive)
                    }
                    if (isActive()) {
                        if (final != null) applyAutomaticResult(query, final, isActive)
                        else recordAutomaticFailure(query)
                    }
                } else {
                    val fetched = resolver.fetchManual(manual) ?: return@start
                    val cached = resolver.toCache(fetched)
                    synchronized(selectionLock) {
                        if (!isActive() || cache.getOverride(query)?.candidate != manual) return@start
                        cache.putLyrics(query, cached)
                        notifiedFailures.remove(query.key)
                        if (current.get()?.key == query.key) {
                            offer(query, fetched.document)
                            refreshOrNotify(query)
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                if (isActive() && manual == null) recordAutomaticFailure(query)
            }
        }

    private fun applyAutomaticResult(query: TrackQuery, result: ResolvedLyrics, isActive: () -> Boolean) = synchronized(selectionLock) {
        if (closed || !isActive()) return@synchronized
        if (automaticWasSuperseded(query)) return@synchronized
        if (result.selectionKey != resolver.selectionKey()) return@synchronized
        val cached = cache.getLyrics(query)?.takeIf { it.selectionKey == result.selectionKey }
        if (cached != null && !resolver.canReplace(cached.document, result.document)) return@synchronized
        if (cached?.document == result.document) return@synchronized
        cache.putLyrics(query, resolver.toCache(result))
        notifiedFailures.remove(query.key)
        if (current.get()?.key == query.key) {
            offer(query, result.document)
            refreshOrNotify(query)
        }
    }

    private fun startQualityUpgradeIfNeeded(query: TrackQuery, resolved: ResolvedLyrics) {
        if (!resolver.shouldUpgrade(resolved.document)) return
        if (tasks.contains(upgradeKey(query)) || !qualityUpgradeAttempts.allow(query.key, force = false)) return
        tasks.start(upgradeKey(query)) { isActive ->
            if (automaticWasSuperseded(query)) return@start
            val upgraded = resolver.resolveAutomaticFully(prepareQuery(query))
            if (!isActive() || automaticWasSuperseded(query)) return@start
            val cached = cache.getLyrics(query) ?: return@start
            if (cached.document != resolved.document) return@start
            if (upgraded != null && resolver.canReplace(cached.document, upgraded.document)) {
                applyAutomaticResult(query, upgraded, isActive)
            }
        }
    }

    private fun upgradeKey(query: TrackQuery): String = "${query.key}/upgrade"

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
    )

    private fun recordAutomaticFailure(query: TrackQuery) {
        if (automaticWasSuperseded(query)) return
        notifyAutomaticFailure(query)
    }

    private fun automaticWasSuperseded(query: TrackQuery): Boolean =
        cache.getOverride(query)?.let { it.local || it.candidate != null } == true

    private fun notifyAutomaticFailure(query: TrackQuery) {
        if (current.get()?.key == query.key && notifiedFailures.add(query.key)) {
            notify("自动加载歌词失败；可从播放器“更多 → 手动搜索歌词”重新搜索。")
        }
    }

    private fun refreshOrNotify(query: TrackQuery) {
        refreshRetrier.refresh { current.get()?.key == query.key }
    }

    private fun render(query: TrackQuery, document: LyricsDocument, encoded: String? = null): String? {
        val delay = delays?.get(query) ?: 0
        if (output != null) return output.render(query, document, delay, encoded)
        if (document.isInstrumental) return ""
        return if (delay == 0 && encoded != null) encoded else SpwLyricsEncoder.encode(document, delay)
    }

    private fun offer(query: TrackQuery, document: LyricsDocument) {
        output?.offer(query, document, delays?.get(query) ?: 0)
    }

    override fun close() {
        synchronized(selectionLock) {
            closed = true
            current.set(null)
            tasks.close()
        }
        localLyrics.set(null)
        output?.close()
        resolver.close()
    }

    private companion object {
        const val INITIAL_RESOLUTION_SECONDS = 10L
        const val QUALITY_UPGRADE_RETRY_SECONDS = 60L
    }
}
