package dev.gaboron.spwlyrics.domain

import kotlin.math.abs

data class CandidateScore(
    val candidate: LyricsCandidate,
    val score: Double,
    val titleScore: Double,
    val artistScore: Double?,
    val albumScore: Double?,
    val durationScore: Double?,
    val versionConflict: Boolean,
) {
    val passesAutomaticGate: Boolean
        get() = !versionConflict &&
            titleScore >= MatchEngine.MIN_TITLE &&
            (artistScore == null || artistScore >= MatchEngine.MIN_ARTIST) &&
            score >= MatchEngine.MIN_TOTAL
}
data class MatchDecision(
    val winner: CandidateScore?,
    val ranked: List<CandidateScore>,
    val ambiguous: Boolean,
)

object MatchEngine {
    const val MIN_TITLE = 0.90
    const val MIN_ARTIST = 0.80
    const val MIN_TOTAL = 0.88
    const val MIN_GAP = 0.08

    fun score(query: TrackQuery, candidate: LyricsCandidate): CandidateScore {
        val title = TextNormalizer.similarity(query.title, candidate.title)
        val artist = if (query.artists.isEmpty() || candidate.artists.isEmpty()) {
            null
        } else {
            query.artists.map { local ->
                candidate.artists.maxOf { remote -> TextNormalizer.similarity(local, remote) }
            }.average()
        }
        val album = if (query.album.isBlank() || candidate.album.isBlank()) {
            null
        } else {
            TextNormalizer.similarity(query.album, candidate.album)
        }
        val duration = durationSimilarity(query.durationMs, candidate.durationMs)

        val components = buildList {
            add(title to 0.55)
            artist?.let { add(it to 0.30) }
            album?.let { add(it to 0.10) }
            duration?.let { add(it to 0.05) }
        }
        val totalWeight = components.sumOf { it.second }
        val raw = components.sumOf { (value, weight) -> value * weight } / totalWeight

        val localVersions = TextNormalizer.versionTokens(query.title)
        val remoteVersions = TextNormalizer.versionTokens(candidate.title)
        val conflict = localVersions != remoteVersions && (localVersions.isNotEmpty() || remoteVersions.isNotEmpty())

        return CandidateScore(candidate, if (conflict) raw - 0.25 else raw, title, artist, album, duration, conflict)
    }

    fun decide(query: TrackQuery, candidates: List<LyricsCandidate>): MatchDecision {
        if (query.title.isBlank() || query.artists.isEmpty()) return MatchDecision(null, emptyList(), false)
        val ranked = candidates.map { score(query, it) }
            .sortedWith(compareByDescending<CandidateScore> { it.score }.thenBy { it.candidate.remoteId })
        val best = ranked.firstOrNull() ?: return MatchDecision(null, ranked, false)
        if (!best.passesAutomaticGate) return MatchDecision(null, ranked, false)
        val second = ranked.drop(1).firstOrNull { it.candidate.remoteId != best.candidate.remoteId }
        val ambiguous = second != null && best.score - second.score < MIN_GAP
        return MatchDecision(if (ambiguous) null else best, ranked, ambiguous)
    }

    private fun durationSimilarity(local: Long?, remote: Long?): Double? {
        if (local == null || remote == null || local <= 0 || remote <= 0) return null
        return when (abs(local - remote)) {
            in 0..1_500 -> 1.0
            in 1_501..3_000 -> 0.85
            in 3_001..5_000 -> 0.60
            in 5_001..8_000 -> 0.30
            else -> 0.0
        }
    }
}
