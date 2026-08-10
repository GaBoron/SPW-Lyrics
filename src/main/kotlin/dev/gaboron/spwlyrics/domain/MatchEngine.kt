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
        get() {
            if (versionConflict) return false
            if (durationScore != null && durationScore < MatchEngine.MIN_DURATION) return false
            val titleAndArtist = titleScore >= MatchEngine.MIN_TITLE &&
                artistScore != null && artistScore >= MatchEngine.MIN_ARTIST &&
                score >= MatchEngine.MIN_TOTAL
            val titleAndAlbum = titleScore >= MatchEngine.STRONG_TITLE &&
                albumScore != null && albumScore >= MatchEngine.MIN_ALBUM &&
                score >= MatchEngine.MIN_TITLE_ALBUM_TOTAL
            val corroboratedFields = titleScore >= MatchEngine.RELAXED_TITLE &&
                artistScore != null && artistScore >= MatchEngine.STRONG_ARTIST &&
                albumScore != null && albumScore >= MatchEngine.STRONG_ALBUM &&
                score >= MatchEngine.MIN_TOTAL
            return titleAndArtist || titleAndAlbum || corroboratedFields
        }
}
data class MatchDecision(
    val winner: CandidateScore?,
    val ranked: List<CandidateScore>,
    val ambiguous: Boolean,
)

object MatchEngine {
    const val RELAXED_TITLE = 0.78
    const val MIN_TITLE = 0.84
    const val STRONG_TITLE = 0.90
    const val MIN_ARTIST = 0.72
    const val STRONG_ARTIST = 0.88
    const val MIN_ALBUM = 0.78
    const val STRONG_ALBUM = 0.82
    const val MIN_TOTAL = 0.80
    const val MIN_TITLE_ALBUM_TOTAL = 0.82
    const val MIN_DURATION = 0.60
    const val MIN_GAP = 0.04

    fun score(query: TrackQuery, candidate: LyricsCandidate): CandidateScore {
        val title = TextNormalizer.similarity(query.title, candidate.title)
        val artist = artistSimilarity(query, candidate)
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
            duration?.let { add(it to 0.20) }
        }
        val totalWeight = components.sumOf { it.second }
        val raw = components.sumOf { (value, weight) -> value * weight } / totalWeight

        val localVersions = TextNormalizer.versionTokens(query.title)
        val remoteVersions = TextNormalizer.versionTokens(candidate.title)
        val conflict = localVersions != remoteVersions && (localVersions.isNotEmpty() || remoteVersions.isNotEmpty())

        return CandidateScore(candidate, if (conflict) raw - 0.25 else raw, title, artist, album, duration, conflict)
    }

    fun decide(query: TrackQuery, candidates: List<LyricsCandidate>): MatchDecision {
        if (query.title.isBlank() || (query.artists.isEmpty() && query.album.isBlank())) {
            return MatchDecision(null, emptyList(), false)
        }
        val ranked = candidates.map { score(query, it) }
            .sortedWith(
                compareByDescending<CandidateScore> { it.score }
                    .thenByDescending { it.candidate.qualityHint?.rank ?: -1 }
                    .thenBy { it.candidate.remoteId },
            )
        val best = ranked.firstOrNull() ?: return MatchDecision(null, ranked, false)
        if (!best.passesAutomaticGate) return MatchDecision(null, ranked, false)
        val second = ranked.drop(1).firstOrNull {
            it.passesAutomaticGate && !sameMetadata(best.candidate, it.candidate)
        }
        val ambiguous = second != null && best.score - second.score < MIN_GAP
        return MatchDecision(if (ambiguous) null else best, ranked, ambiguous)
    }

    private fun artistSimilarity(query: TrackQuery, candidate: LyricsCandidate): Double? {
        val localArtists = (query.artists + query.albumArtists)
            .distinctBy(TextNormalizer::compact)
        if (localArtists.isEmpty() || candidate.artists.isEmpty()) return null

        val localCoverage = localArtists.map { local ->
            candidate.artists.maxOf { remote -> TextNormalizer.similarity(local, remote) }
        }.average()
        val remoteCoverage = candidate.artists.map { remote ->
            localArtists.maxOf { local -> TextNormalizer.similarity(local, remote) }
        }.average()
        val strongestPair = localArtists.maxOf { local ->
            candidate.artists.maxOf { remote -> TextNormalizer.similarity(local, remote) }
        }
        val balancedCoverage = minOf(localCoverage, remoteCoverage) * 0.35 +
            maxOf(localCoverage, remoteCoverage) * 0.65
        return maxOf(strongestPair * 0.85, balancedCoverage).coerceIn(0.0, 1.0)
    }

    private fun sameMetadata(left: LyricsCandidate, right: LyricsCandidate): Boolean =
        TextNormalizer.compact(left.title) == TextNormalizer.compact(right.title) &&
            left.artists.map(TextNormalizer::compact).toSet() == right.artists.map(TextNormalizer::compact).toSet() &&
            TextNormalizer.compact(left.album) == TextNormalizer.compact(right.album)

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
