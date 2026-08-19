package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.CachedLyrics
import java.time.Clock

data class ResolvedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
    val encoded: String,
)

class LyricsResolver(
    providers: List<LyricsProvider>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val providers = providers.associateBy(LyricsProvider::source)
    private val orderedSources = LyricsSource.entries.sortedBy(LyricsSource::priority)
    private val manualCandidateInspector = ManualCandidateInspector()

    fun resolveAutomatic(query: TrackQuery, deadlineNanos: Long = Long.MAX_VALUE): ResolvedLyrics? {
        val selection = AutomaticLyricsSelection()
        val fetchedBySource = linkedMapOf<LyricsSource, LyricsDocument>()
        for (source in orderedSources) {
            if (System.nanoTime() >= deadlineNanos) break
            if (source == LyricsSource.LOCAL) break
            val provider = providers[source] ?: continue
            val winner = findWinner(provider, query, deadlineNanos) ?: continue
            if (System.nanoTime() >= deadlineNanos) break
            val document = fetchDocument(provider, winner) ?: continue
            fetchedBySource[source] = document
            selection.consider(FetchedLyrics(winner, document))?.let { fetched ->
                return finalize(fetched, query, deadlineNanos, fetchedBySource)
            }
        }
        return selection.fallback()?.let { finalize(it, query, deadlineNanos, fetchedBySource) }
    }

    fun searchManual(query: TrackQuery, keywords: String, source: LyricsSource?): List<CandidateScore> {
        val selected = if (source == null) {
            orderedSources.filter { it != LyricsSource.LOCAL }.mapNotNull(providers::get)
        } else {
            listOfNotNull(providers[source])
        }
        val requests = selected.flatMap { provider ->
            searchManual(provider, query, keywords).map { ManualCandidateRequest(provider, it) }
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
        finalize(FetchedLyrics(candidate, document), query)
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

    private fun finalize(
        fetched: FetchedLyrics,
        query: TrackQuery,
        deadlineNanos: Long = Long.MAX_VALUE,
        fetchedBySource: Map<LyricsSource, LyricsDocument> = emptyMap(),
    ): ResolvedLyrics? {
        val document = if (fetched.candidate.source in PRIMARY_WORD_SOURCES) {
            enrichFromOtherSources(fetched.document, fetched.candidate, query, deadlineNanos, fetchedBySource)
        } else {
            fetched.document
        }
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return null
        return ResolvedLyrics(fetched.candidate, document, encoded)
    }

    private fun enrichFromOtherSources(
        primary: LyricsDocument,
        primaryCandidate: LyricsCandidate,
        query: TrackQuery,
        deadlineNanos: Long,
        fetchedBySource: Map<LyricsSource, LyricsDocument>,
    ): LyricsDocument {
        if (!SecondaryLyricsEnricher.needsTranslation(primary)) return primary
        var enriched = primary
        val lookupQueries = TranslationLookupPlan.queries(query, primaryCandidate)
        for (source in TRANSLATION_SOURCES) {
            if (System.nanoTime() >= deadlineNanos) break
            val provider = providers[source] ?: continue
            val next = fetchedBySource[source]
                ?.let { SecondaryLyricsEnricher.enrich(enriched, it).takeIf { result -> result != enriched } }
                ?: findTranslationEnrichment(provider, lookupQueries, enriched, deadlineNanos)
                ?: continue
            enriched = next
            if (!SecondaryLyricsEnricher.needsTranslation(enriched)) break
        }
        return enriched
    }

    private fun findTranslationEnrichment(
        provider: LyricsProvider,
        lookupQueries: List<TrackQuery>,
        primary: LyricsDocument,
        deadlineNanos: Long,
    ): LyricsDocument? {
        val candidates = linkedMapOf<String, LyricsCandidate>()
        val attemptedDownloads = mutableSetOf<String>()
        val downloaded = mutableMapOf<String, LyricsDocument>()

        fun fetchOnce(candidate: LyricsCandidate): LyricsDocument? {
            if (!attemptedDownloads.add(candidate.remoteId)) return downloaded[candidate.remoteId]
            if (System.nanoTime() >= deadlineNanos) return null
            return fetchDocument(provider, candidate)?.also { downloaded[candidate.remoteId] = it }
        }

        fun enrich(candidate: LyricsCandidate, requireRecordingEvidence: Boolean): LyricsDocument? {
            val secondary = fetchOnce(candidate) ?: return null
            val alignment = CrossSourceLyricsAligner.align(primary, secondary)
            if (requireRecordingEvidence && !alignment.provesSameRecording) return null
            return SecondaryLyricsEnricher.enrich(primary, secondary).takeIf { it != primary }
        }

        for (lookupQuery in lookupQueries) {
            for (keywords in lookupQuery.searchQueries().take(TRANSLATION_SEARCH_QUERIES_PER_METADATA)) {
                if (System.nanoTime() >= deadlineNanos) return null
                search(provider, lookupQuery, keywords).forEach { candidates.putIfAbsent(it.remoteId, it) }
                val winner = MatchEngine.decide(
                    lookupQuery,
                    candidates.values.toList(),
                    TranslationMatchPolicy::accepts,
                ).winner?.candidate
                if (winner != null) enrich(winner, requireRecordingEvidence = false)?.let { return it }
            }
        }

        val verifiable = candidates.values.map { candidate ->
            lookupQueries.map { MatchEngine.score(it, candidate) }.maxBy(CandidateScore::score)
        }.filter(TranslationMatchPolicy::canVerifyByLyrics)
            .sortedByDescending(CandidateScore::score)
            .take(MAX_TRANSLATION_CANDIDATES_TO_VERIFY)
        for (candidate in verifiable) {
            if (System.nanoTime() >= deadlineNanos) return null
            enrich(candidate.candidate, requireRecordingEvidence = true)?.let { return it }
        }
        return null
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
        val TRANSLATION_SOURCES = listOf(LyricsSource.QQ, LyricsSource.KUGOU, LyricsSource.NETEASE)
        const val MANUAL_RESULTS_PER_SOURCE = 8
        const val TRANSLATION_SEARCH_QUERIES_PER_METADATA = 3
        const val MAX_TRANSLATION_CANDIDATES_TO_VERIFY = 2
    }
}
