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
        return selected.flatMap { provider -> search(provider, query, keywords).map { MatchEngine.score(query, it) } }
            .sortedWith(
                compareBy<CandidateScore> { it.candidate.source.priority }
                    .thenByDescending { it.candidate.qualityHint?.rank ?: -1 }
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
        val document = fetchDocument(provider, candidate) ?: return@let null
        finalize(FetchedLyrics(candidate, document), query)
    }

    fun toCache(resolved: ResolvedLyrics): CachedLyrics = CachedLyrics(
        document = resolved.document,
        encoded = resolved.encoded,
        savedAtEpochMs = clock.millis(),
    )

    private fun search(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        runCatching { provider.search(query, keywords) }.getOrDefault(emptyList())

    private fun finalize(
        fetched: FetchedLyrics,
        query: TrackQuery,
        deadlineNanos: Long = Long.MAX_VALUE,
        fetchedBySource: Map<LyricsSource, LyricsDocument> = emptyMap(),
    ): ResolvedLyrics? {
        val document = if (fetched.candidate.source in PRIMARY_WORD_SOURCES) {
            enrichFromOtherSources(fetched.document, query, deadlineNanos, fetchedBySource)
        } else {
            fetched.document
        }
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return null
        return ResolvedLyrics(fetched.candidate, document, encoded)
    }

    private fun enrichFromOtherSources(
        primary: LyricsDocument,
        query: TrackQuery,
        deadlineNanos: Long,
        fetchedBySource: Map<LyricsSource, LyricsDocument>,
    ): LyricsDocument {
        if (!SecondaryLyricsEnricher.needsTranslation(primary)) return primary
        var enriched = primary
        for (source in TRANSLATION_SOURCES) {
            if (System.nanoTime() >= deadlineNanos) break
            val provider = providers[source] ?: continue
            val secondary = fetchedBySource[source] ?: run {
                val winner = findWinner(provider, query, deadlineNanos) ?: return@run null
                if (System.nanoTime() >= deadlineNanos) return@run null
                fetchDocument(provider, winner)
            } ?: continue
            enriched = SecondaryLyricsEnricher.enrich(enriched, secondary)
            if (!SecondaryLyricsEnricher.needsTranslation(enriched)) break
        }
        return enriched
    }

    private fun fetchDocument(provider: LyricsProvider, candidate: LyricsCandidate): LyricsDocument? =
        runCatching { provider.fetch(candidate) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    private fun findWinner(
        provider: LyricsProvider,
        query: TrackQuery,
        deadlineNanos: Long,
    ): LyricsCandidate? {
        val candidates = linkedMapOf<String, LyricsCandidate>()
        for (keywords in query.searchQueries()) {
            if (System.nanoTime() >= deadlineNanos) break
            search(provider, query, keywords).forEach { candidates.putIfAbsent(it.remoteId, it) }
            MatchEngine.decide(query, candidates.values.toList()).winner?.candidate?.let { return it }
        }
        return MatchEngine.decide(query, candidates.values.toList()).winner?.candidate
    }

    private companion object {
        val PRIMARY_WORD_SOURCES = setOf(LyricsSource.AMLL, LyricsSource.APPLE_MUSIC)
        val TRANSLATION_SOURCES = listOf(LyricsSource.QQ, LyricsSource.KUGOU, LyricsSource.NETEASE)
    }
}
