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
import dev.gaboron.spwlyrics.storage.LyricsCache
import java.time.Clock

data class ResolvedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
    val encoded: String,
)

class LyricsResolver(
    providers: List<LyricsProvider>,
    private val cache: LyricsCache,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val providers = providers.associateBy(LyricsProvider::source)
    private val orderedSources = LyricsSource.entries.sortedBy(LyricsSource::priority)

    fun resolveAutomatic(query: TrackQuery, deadlineNanos: Long = Long.MAX_VALUE): ResolvedLyrics? {
        for (source in orderedSources) {
            if (System.nanoTime() >= deadlineNanos) return null
            if (source == LyricsSource.LOCAL) return null
            val provider = providers[source] ?: continue
            val candidates = query.searchQueries().takeWhile { System.nanoTime() < deadlineNanos }
                .flatMap { keywords -> search(provider, query, keywords) }
                .distinctBy { it.remoteId }
            val decision = MatchEngine.decide(query, candidates)
            val winner = decision.winner?.candidate ?: continue
            if (System.nanoTime() >= deadlineNanos) return null
            fetch(provider, winner)?.let { return it }
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

    fun fetchManual(candidate: LyricsCandidate): ResolvedLyrics? = providers[candidate.source]?.let { fetch(it, candidate) }

    fun toCache(resolved: ResolvedLyrics): CachedLyrics = CachedLyrics(
        document = resolved.document,
        encoded = resolved.encoded,
        savedAtEpochMs = clock.millis(),
    )

    private fun search(provider: LyricsProvider, query: TrackQuery, keywords: String): List<LyricsCandidate> =
        cache.getSearch(provider.source, keywords) ?: runCatching { provider.search(query, keywords) }
            .getOrDefault(emptyList())
            .also { cache.putSearch(provider.source, keywords, it) }

    private fun fetch(provider: LyricsProvider, candidate: LyricsCandidate): ResolvedLyrics? {
        val document = runCatching { provider.fetch(candidate) }.getOrNull()?.takeIf { it.lines.isNotEmpty() } ?: return null
        val encoded = SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank) ?: return null
        return ResolvedLyrics(candidate, document, encoded)
    }
}
