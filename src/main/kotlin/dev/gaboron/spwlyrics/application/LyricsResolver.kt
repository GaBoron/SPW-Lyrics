package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import dev.gaboron.spwlyrics.domain.LyricsPreferences
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.CachedLyrics
import java.time.Clock
import java.util.concurrent.TimeUnit

data class ResolvedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
    val selectionKey: String = "",
)

internal data class FetchedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
)

class LyricsResolver(
    providers: List<LyricsProvider>,
    private val clock: Clock = Clock.systemUTC(),
    private val preferences: () -> LyricsPreferences = { LyricsPreferences() },
) : AutoCloseable {
    private val providers = providers.associateBy(LyricsProvider::source)
    private val manualCandidateInspector = ManualCandidateInspector()
    private val providerTasks = ProviderTaskPool()

    fun resolveAutomaticProgressively(
        query: TrackQuery,
        snapshotDeadlineNanos: Long,
        onSnapshot: (ResolvedLyrics) -> Unit,
    ): ResolvedLyrics? = resolveAutomatic(query, snapshotDeadlineNanos, LyricsQuality.PLAIN, onSnapshot)

    fun resolveAutomaticFully(
        query: TrackQuery,
        onProgress: (LyricsResolutionProgress) -> Unit = {},
    ): ResolvedLyrics? {
        val now = System.nanoTime()
        return resolveAutomatic(
            query = query,
            snapshotDeadlineNanos = now,
            minimumQuality = LyricsQuality.PLAIN,
            onSnapshot = {},
            onProgress = onProgress,
            completionDeadlineNanos = now + TimeUnit.MILLISECONDS.toNanos(FULL_SEARCH_TIMEOUT_MILLIS),
        )
    }

    private fun resolveAutomatic(
        query: TrackQuery,
        snapshotDeadlineNanos: Long,
        minimumQuality: LyricsQuality,
        onSnapshot: (ResolvedLyrics) -> Unit,
        onProgress: (LyricsResolutionProgress) -> Unit = {},
        completionDeadlineNanos: Long = Long.MAX_VALUE,
    ): ResolvedLyrics? {
        val order = preferences()
        val selected = order.sources.filter { it != LyricsSource.LOCAL && it.hasLyrics }.mapNotNull(providers::get)
        onProgress(LyricsResolutionProgress(LyricsResolutionStage.SEARCHING, 0.04, "开始并行搜索 ${selected.size} 个歌词来源"))
        val completed = providerTasks.collectProgressively(
            tasks = selected.map { provider ->
                {
                    resolveProvider(
                        provider,
                        query,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROVIDER_SEARCH_BUDGET_MILLIS),
                        minimumQuality,
                    )
                }
            },
            snapshotDeadlineNanos = snapshotDeadlineNanos,
            completionDeadlineNanos = completionDeadlineNanos,
            snapshotWhen = { results, pending -> unbeatable(results, pending, selected, order) },
            stopWhen = { results, pending -> unbeatable(results, pending, selected, order) },
            onProgress = { results, pending ->
                val finished = selected.size - pending.size
                val pendingNames = pending.joinToString("、") { selected[it].source.displayName }
                val waiting = pendingNames.takeIf(String::isNotBlank)?.let { "；等待：$it" }.orEmpty()
                val best = LyricsSelectionPolicy.select(results.map(IndexedValue<FetchedLyrics>::value), order)
                val detail = best?.let {
                    "已检查 $finished/${selected.size} 个来源，当前最佳：${it.candidate.source.displayName}$waiting"
                } ?: "已检查 $finished/${selected.size} 个来源$waiting"
                onProgress(
                    LyricsResolutionProgress(
                        LyricsResolutionStage.SEARCHING,
                        0.04 + 0.66 * finished / selected.size.coerceAtLeast(1),
                        detail,
                    ),
                )
            },
        ) { snapshot ->
            selectResolved(snapshot, order)?.let(onSnapshot)
        }
        val resolved = selectResolved(completed, order)
        onProgress(
            LyricsResolutionProgress(
                LyricsResolutionStage.SELECTING,
                0.76,
                resolved?.let { "已选定 ${it.document.source.displayName}，正在检查翻译" } ?: "没有找到可靠歌词",
            ),
        )
        return resolved
    }

    fun searchManual(query: TrackQuery, keywords: String, source: LyricsSource?,
        onStatus: (ProviderSearchStatus) -> Unit = {}): List<CandidateScore> {
        val selected = if (source == null) {
            preferences().sources.filter { it != LyricsSource.LOCAL }.mapNotNull(providers::get)
        } else {
            listOfNotNull(providers[source])
        }
        return ManualLyricsSearch(providerTasks, manualCandidateInspector, preferences = preferences()).search(selected, query, keywords, onStatus)
    }

    fun fetchManual(candidate: LyricsCandidate): ResolvedLyrics? = providers[candidate.source]?.let { provider ->
        val document = manualCandidateInspector.cached(candidate) ?: fetchDocument(provider, candidate) ?: return@let null
        resolved(FetchedLyrics(candidate, document))
    }

    fun toCache(resolved: ResolvedLyrics): CachedLyrics = CachedLyrics(
        document = resolved.document,
        encoded = if (resolved.document.rawTtml == null) SpwLyricsEncoder.encode(resolved.document) else null,
        savedAtEpochMs = clock.millis(),
        selectionKey = resolved.selectionKey,
    )

    private fun search(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        runCatching { provider.search(query, keywords) }.getOrDefault(emptyList())

    private fun resolved(fetched: FetchedLyrics): ResolvedLyrics? =
        fetched.document.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            ?.let { ResolvedLyrics(fetched.candidate, it) }

    fun selectionPreferences(): LyricsPreferences = preferences()
    fun metadata(candidate: LyricsCandidate): LyricsCandidate = providers[candidate.source]?.metadata(candidate) ?: candidate
    fun selectionKey(): String = preferences().key
    fun canReplace(current: LyricsDocument, replacement: LyricsDocument): Boolean = LyricsSelectionPolicy.canReplace(current, replacement, preferences())
    fun shouldUpgrade(document: LyricsDocument): Boolean = preferences().canImprove(document)

    private fun selectResolved(completed: List<IndexedValue<FetchedLyrics>>, order: LyricsPreferences): ResolvedLyrics? =
        LyricsSelectionPolicy.select(completed.map(IndexedValue<FetchedLyrics>::value), order)?.let(::resolved)?.copy(selectionKey = order.key)

    private fun unbeatable(
        completed: List<IndexedValue<FetchedLyrics>>,
        pending: Set<Int>,
        selected: List<LyricsProvider>,
        order: LyricsPreferences,
    ): Boolean {
        val best = LyricsSelectionPolicy.select(completed.map { it.value }, order) ?: return false
        return pending.none { order.compare(selected[it].source, order.qualities.first(), best.candidate.source, best.document.quality) < 0 }
    }

    private fun resolveProvider(
        provider: LyricsProvider,
        query: TrackQuery,
        deadlineNanos: Long,
        minimumQuality: LyricsQuality,
    ): FetchedLyrics? {
        val winner = findWinner(provider, query, deadlineNanos) ?: return null
        if (System.nanoTime() >= deadlineNanos) return null
        val document = fetchDocument(provider, winner)
            ?.takeIf { it.quality.rank >= minimumQuality.rank }
            ?: return null
        return FetchedLyrics(winner, document)
    }

    override fun close() {
        providerTasks.close()
        providers.values.filterIsInstance<AutoCloseable>().forEach { provider -> runCatching { provider.close() } }
    }

    private fun fetchDocument(provider: LyricsProvider, candidate: LyricsCandidate): LyricsDocument? =
        runCatching { provider.fetch(candidate) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    private fun findWinner(
        provider: LyricsProvider,
        query: TrackQuery,
        deadlineNanos: Long,
        accepts: (CandidateScore) -> Boolean = PreferredSourceMatchPolicy::accepts,
    ): LyricsCandidate? {
        val candidates = linkedMapOf<String, LyricsCandidate>()
        for (keywords in provider.automaticSearchQueries(query)) {
            if (System.nanoTime() >= deadlineNanos) break
            search(provider, query, keywords).forEach { candidates.putIfAbsent(it.remoteId, it) }
            MatchEngine.decide(query, candidates.values.toList(), accepts)
                .winner?.candidate?.let { return it }
        }
        return MatchEngine.decide(query, candidates.values.toList(), accepts).winner?.candidate
    }

    private companion object {
        const val PROVIDER_SEARCH_BUDGET_MILLIS = 12_000L
        const val FULL_SEARCH_TIMEOUT_MILLIS = 20_000L
    }
}
