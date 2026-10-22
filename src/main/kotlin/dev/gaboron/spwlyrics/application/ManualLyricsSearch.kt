package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.provider.ProviderSearchFailure
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps each provider's outcome through parallel search without delaying results for status probes. */
internal class ManualLyricsSearch(
    private val tasks: ProviderTaskPool,
    private val inspector: ManualCandidateInspector,
    private val timeoutMillis: Long = 6_000,
) {
    fun search(providers: List<LyricsProvider>, query: TrackQuery, keywords: String,
        onStatus: (ProviderSearchStatus) -> Unit): List<CandidateScore> {
        val accepting = providers.map { AtomicBoolean(true) }
        providers.forEach { onStatus(ProviderSearchStatus(it.source, ProviderSearchPhase.SEARCHING, "正在搜索…")) }
        val results = tasks.collect(providers.mapIndexed { index, provider -> {
            val report: (ProviderSearchStatus) -> Unit = { status ->
                synchronized(accepting[index]) { if (accepting[index].get()) onStatus(status) }
            }
            runCatching { provider.searchManual(query, keywords, 8, report) }.getOrElse { error ->
                report(ProviderSearchFailure.status(provider.source, error))
                emptyList()
            }
        } }, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis))
        val finished = results.map { it.index }.toSet()
        providers.forEachIndexed { index, provider ->
            if (index !in finished) synchronized(accepting[index]) {
                accepting[index].set(false)
                onStatus(ProviderSearchStatus(provider.source, ProviderSearchPhase.TIMED_OUT, "搜索期限内未完成，请稍后重试。"))
            }
        }
        val requests = results.flatMap { result -> result.value.map { ManualCandidateRequest(providers[result.index], it) } }
        return inspector.inspect(requests).map { MatchEngine.score(query, it) }.sortedWith(
            compareByDescending<CandidateScore> { it.candidate.qualityHint?.rank ?: -1 }
                .thenBy { it.candidate.source.priority }.thenByDescending(CandidateScore::score))
    }
}
