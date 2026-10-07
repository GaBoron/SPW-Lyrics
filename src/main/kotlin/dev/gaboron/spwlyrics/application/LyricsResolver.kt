package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.CachedLyrics
import java.time.Clock
import java.util.concurrent.TimeUnit

data class ResolvedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
    val encoded: String,
)

internal data class FetchedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
)

class LyricsResolver(
    providers: List<LyricsProvider>,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private val providers = providers.associateBy(LyricsProvider::source)
    private val orderedSources = LyricsSource.entries.sortedBy(LyricsSource::priority)
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
        val selected = orderedSources.filter { it != LyricsSource.LOCAL }.mapNotNull(providers::get)
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
            snapshotWhen = { results, pending -> hasHighestAvailableSource(results, pending) },
            stopWhen = { results, pending -> hasUnbeatableKaraokeResult(results, pending) },
            onProgress = { results, pending ->
                val finished = selected.size - pending.size
                val pendingNames = pending.joinToString("、") { selected[it].source.displayName }
                val waiting = pendingNames.takeIf(String::isNotBlank)?.let { "；等待：$it" }.orEmpty()
                val best = LyricsSelectionPolicy.select(results.map(IndexedValue<FetchedLyrics>::value))
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
            selectAndEncode(snapshot)?.let(onSnapshot)
        }
        val resolved = selectAndEncode(completed)
        onProgress(
            LyricsResolutionProgress(
                LyricsResolutionStage.SELECTING,
                0.76,
                resolved?.let { "已选定 ${it.document.source.displayName}，正在检查翻译" } ?: "没有找到可靠歌词",
            ),
        )
        return resolved
    }

    fun searchManual(query: TrackQuery, keywords: String, source: LyricsSource?): List<CandidateScore> {
        val selected = if (source == null) {
            orderedSources.filter { it != LyricsSource.LOCAL }.mapNotNull(providers::get)
        } else {
            listOfNotNull(providers[source])
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MANUAL_SEARCH_TIMEOUT_MILLIS)
        val searched = providerTasks.collect(
            selected.map { provider -> { searchManual(provider, query, keywords) } },
            deadline,
        )
        val requests = searched.flatMap { result ->
            val provider = selected[result.index]
            result.value.map { ManualCandidateRequest(provider, it) }
        }
        return manualCandidateInspector.inspect(requests)
            .map { MatchEngine.score(query, it) }
            .sortedWith(
                compareByDescending<CandidateScore> { it.candidate.qualityHint?.rank ?: -1 }
                    .thenBy { it.candidate.source.priority }
                    .thenByDescending(CandidateScore::score),
            )
    }

    fun fetchManual(candidate: LyricsCandidate): ResolvedLyrics? = providers[candidate.source]?.let { provider ->
        val query = TrackQuery(
            title = candidate.title,
            artists = candidate.artists,
            album = candidate.album,
            durationMs = candidate.durationMs,
            externalIds = candidate.externalIds,
        )
        val document = manualCandidateInspector.cached(candidate) ?: fetchDocument(provider, candidate) ?: return@let null
        encode(FetchedLyrics(candidate, document))
    }

    fun toCache(resolved: ResolvedLyrics): CachedLyrics = CachedLyrics(
        document = resolved.document,
        encoded = resolved.encoded,
        savedAtEpochMs = clock.millis(),
    )

    private fun search(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        runCatching { provider.search(query, keywords) }.getOrDefault(emptyList())

    private fun searchManual(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        runCatching { provider.searchManual(query, keywords, MANUAL_RESULTS_PER_SOURCE) }.getOrDefault(emptyList())

    private fun encode(fetched: FetchedLyrics): ResolvedLyrics? =
        SpwLyricsEncoder.encode(fetched.document).takeIf(String::isNotBlank)
            ?.let { ResolvedLyrics(fetched.candidate, fetched.document, it) }

    private fun selectAndEncode(completed: List<IndexedValue<FetchedLyrics>>): ResolvedLyrics? =
        LyricsSelectionPolicy.select(completed.map(IndexedValue<FetchedLyrics>::value))?.let(::encode)

    private fun hasUnbeatableKaraokeResult(
        completed: List<IndexedValue<FetchedLyrics>>,
        pending: Set<Int>,
    ): Boolean {
        val bestKaraoke = completed.filter { it.value.document.quality == LyricsQuality.KARAOKE_SYNCED }
            .minByOrNull(IndexedValue<FetchedLyrics>::index)
        return bestKaraoke != null && pending.none { it < bestKaraoke.index }
    }

    private fun hasHighestAvailableSource(
        completed: List<IndexedValue<FetchedLyrics>>,
        pending: Set<Int>,
    ): Boolean {
        val bestSource = completed.minByOrNull(IndexedValue<FetchedLyrics>::index) ?: return false
        return pending.none { it < bestSource.index }
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
        const val MANUAL_RESULTS_PER_SOURCE = 8
        const val MANUAL_SEARCH_TIMEOUT_MILLIS = 6_000L
        const val PROVIDER_SEARCH_BUDGET_MILLIS = 12_000L
        const val FULL_SEARCH_TIMEOUT_MILLIS = 20_000L
    }
}
