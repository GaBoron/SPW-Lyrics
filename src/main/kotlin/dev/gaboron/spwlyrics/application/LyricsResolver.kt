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
    private val translationSources = TranslationSourceResolver(this.providers, providerTasks)

    fun resolveAutomaticProgressively(
        query: TrackQuery,
        snapshotDeadlineNanos: Long,
        onSnapshot: (ResolvedLyrics) -> Unit,
    ): ResolvedLyrics? = resolveAutomatic(query, snapshotDeadlineNanos, LyricsQuality.PLAIN, onSnapshot)

    fun resolveAutomaticFully(query: TrackQuery): ResolvedLyrics? =
        resolveAutomatic(query, System.nanoTime(), LyricsQuality.PLAIN) {}

    fun resolveKaraokeTimed(query: TrackQuery, deadlineNanos: Long): ResolvedLyrics? =
        resolveAutomatic(query, deadlineNanos, LyricsQuality.WORD_SYNCED) {}

    private fun resolveAutomatic(
        query: TrackQuery,
        snapshotDeadlineNanos: Long,
        minimumQuality: LyricsQuality,
        onSnapshot: (ResolvedLyrics) -> Unit,
    ): ResolvedLyrics? {
        val selected = orderedSources.filter { it != LyricsSource.LOCAL }.mapNotNull(providers::get)
        val completed = providerTasks.collectProgressively(
            tasks = selected.map { provider -> { resolveProvider(provider, query, Long.MAX_VALUE, minimumQuality) } },
            snapshotDeadlineNanos = snapshotDeadlineNanos,
            snapshotWhen = { results, pending -> hasHighestAvailableSource(results, pending) },
            stopWhen = { results, pending -> hasUnbeatableCharacterResult(results, pending) },
        ) { snapshot ->
            selectAndEncode(snapshot)?.let(onSnapshot)
        }
        return selectAndEncode(completed)
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

    fun enrichTranslation(
        resolved: ResolvedLyrics,
        query: TrackQuery,
        deadlineNanos: Long,
    ): ResolvedLyrics {
        if (resolved.candidate.source !in PRIMARY_WORD_SOURCES) return resolved
        val document = enrichFromOtherSources(resolved.document, resolved.candidate, query, deadlineNanos)
        if (document == resolved.document) return resolved
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return resolved
        return resolved.copy(document = document, encoded = encoded)
    }

    fun enrichTranslationFully(resolved: ResolvedLyrics, query: TrackQuery): ResolvedLyrics {
        if (resolved.candidate.source !in PRIMARY_WORD_SOURCES) return resolved
        if (!SecondaryLyricsEnricher.needsTranslation(resolved.document)) return resolved
        val match = translationSources.findAll(resolved.document, resolved.candidate, query) ?: return resolved
        val document = SecondaryLyricsEnricher.enrich(resolved.document, match.document, match.alignment)
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return resolved
        return resolved.copy(document = document, encoded = encoded)
    }

    fun needsTranslationEnrichment(resolved: ResolvedLyrics): Boolean =
        resolved.candidate.source in PRIMARY_WORD_SOURCES && SecondaryLyricsEnricher.needsTranslation(resolved.document)

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

    private fun hasUnbeatableCharacterResult(
        completed: List<IndexedValue<FetchedLyrics>>,
        pending: Set<Int>,
    ): Boolean {
        val bestCharacter = completed.filter { it.value.document.quality == LyricsQuality.CHARACTER_SYNCED }
            .minByOrNull(IndexedValue<FetchedLyrics>::index)
        return bestCharacter != null && pending.none { it < bestCharacter.index }
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

    private fun enrichFromOtherSources(
        primary: LyricsDocument,
        primaryCandidate: LyricsCandidate,
        query: TrackQuery,
        deadlineNanos: Long,
    ): LyricsDocument {
        if (!SecondaryLyricsEnricher.needsTranslation(primary)) return primary
        val match = translationSources.find(primary, primaryCandidate, query, deadlineNanos) ?: return primary
        return SecondaryLyricsEnricher.enrich(primary, match.document, match.alignment)
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
        for (keywords in query.searchQueries()) {
            if (System.nanoTime() >= deadlineNanos) break
            search(provider, query, keywords).forEach { candidates.putIfAbsent(it.remoteId, it) }
            MatchEngine.decide(query, candidates.values.toList(), accepts)
                .winner?.candidate?.let { return it }
        }
        return MatchEngine.decide(query, candidates.values.toList(), accepts).winner?.candidate
    }

    private companion object {
        val PRIMARY_WORD_SOURCES = setOf(LyricsSource.AMLL, LyricsSource.APPLE_MUSIC)
        const val MANUAL_RESULTS_PER_SOURCE = 8
        const val MANUAL_SEARCH_TIMEOUT_MILLIS = 6_000L
    }
}
