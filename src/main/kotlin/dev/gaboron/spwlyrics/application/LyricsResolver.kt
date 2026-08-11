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
        for (source in orderedSources) {
            if (System.nanoTime() >= deadlineNanos) return null
            if (source == LyricsSource.LOCAL) return null
            val provider = providers[source] ?: continue
            val winner = findWinner(provider, query, deadlineNanos) ?: continue
            if (System.nanoTime() >= deadlineNanos) return null
            fetch(provider, winner, query, deadlineNanos)?.let { return it }
        }
        return null
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
        fetch(provider, candidate, query)
    }

    fun toCache(resolved: ResolvedLyrics): CachedLyrics = CachedLyrics(
        document = resolved.document,
        encoded = resolved.encoded,
        savedAtEpochMs = clock.millis(),
    )

    private fun search(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        runCatching { provider.search(query, keywords) }.getOrDefault(emptyList())

    private fun fetch(
        provider: LyricsProvider,
        candidate: LyricsCandidate,
        query: TrackQuery,
        deadlineNanos: Long = Long.MAX_VALUE,
    ): ResolvedLyrics? {
        val fetched = fetchDocument(provider, candidate) ?: return null
        val document = if (candidate.source in PRIMARY_WORD_SOURCES) {
            enrichFromOtherSources(fetched, query, deadlineNanos)
        } else {
            fetched
        }
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return null
        return ResolvedLyrics(candidate, document, encoded)
    }

    private fun enrichFromOtherSources(
        primary: LyricsDocument,
        query: TrackQuery,
        deadlineNanos: Long,
    ): LyricsDocument {
        if (!SecondaryLyricsEnricher.needsTranslation(primary)) return primary
        var enriched = primary
        for (source in TRANSLATION_SOURCES) {
            if (System.nanoTime() >= deadlineNanos) break
            val provider = providers[source] ?: continue
            val winner = findWinner(provider, query, deadlineNanos) ?: continue
            if (System.nanoTime() >= deadlineNanos) break
            val secondary = fetchDocument(provider, winner) ?: continue
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
