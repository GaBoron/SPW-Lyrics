package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.provider.ProviderSearchFailure
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Keeps each provider's outcome through parallel search without delaying results for status probes. */
internal class ManualLyricsSearch(
    private val tasks: ProviderTaskPool,
    private val inspector: ManualCandidateInspector,
    private val timeoutMillis: Long = 6_000,
    private val preferences: LyricsPreferences = LyricsPreferences(),
) {
    fun search(providers: List<LyricsProvider>, query: TrackQuery, keywords: String,
        onStatus: (ProviderSearchStatus) -> Unit): List<CandidateScore> {
        val ready = providers.filter { provider ->
            val configuration = provider.configurationStatus()
            if (configuration != null) onStatus(configuration)
            configuration == null
        }
        val accepting = ready.map { AtomicBoolean(true) }
        val latest = ready.map { AtomicReference(ProviderSearchStatus(it.source, ProviderSearchPhase.SEARCHING, "正在搜索…")) }
        latest.forEach { onStatus(it.get()) }
        val results = tasks.collect(ready.mapIndexed { index, provider -> {
            val report: (ProviderSearchStatus) -> Unit = { status ->
                synchronized(accepting[index]) {
                    if (accepting[index].get() && !Thread.currentThread().isInterrupted) { latest[index].set(status); onStatus(status) }
                }
            }
            runCatching { provider.searchManual(query, keywords, 8, report) }.getOrElse { error ->
                if (error !is InterruptedException && !Thread.currentThread().isInterrupted) {
                    report(ProviderSearchFailure.status(provider.source, error))
                }
                emptyList()
            }
        } }, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis))
        val finished = results.map { it.index }.toSet()
        ready.forEachIndexed { index, provider ->
            if (index !in finished) synchronized(accepting[index]) {
                accepting[index].set(false)
                if (latest[index].get().phase !in setOf(ProviderSearchPhase.NOT_CONFIGURED, ProviderSearchPhase.FAILED, ProviderSearchPhase.TIMED_OUT)) {
                    onStatus(ProviderSearchStatus(provider.source, ProviderSearchPhase.TIMED_OUT, "搜索期限内未完成，请稍后重试。"))
                }
            }
        }
        val candidates = results.flatMap { it.value }.map { candidate ->
            candidate.copy(qualityHint = inspector.cached(candidate)?.quality)
        }
        return candidates.map { MatchEngine.score(query, it) }.sortedWith(
            Comparator<CandidateScore> { left, right ->
                preferences.compare(left.candidate.source, left.candidate.qualityHint ?: LyricsQuality.PLAIN,
                    right.candidate.source, right.candidate.qualityHint ?: LyricsQuality.PLAIN)
            }.thenByDescending(CandidateScore::score))
    }
}
