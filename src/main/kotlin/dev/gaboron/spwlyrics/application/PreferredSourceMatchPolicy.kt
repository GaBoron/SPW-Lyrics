package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine

/** Keeps safety checks while allowing AMLL TTML matches some metadata tolerance. */
internal object PreferredSourceMatchPolicy {
    fun accepts(candidate: CandidateScore): Boolean {
        if (candidate.passesAutomaticGate) return true
        if (candidate.versionConflict) return false
        if (candidate.durationScore != null && candidate.durationScore < MatchEngine.MIN_DURATION) return false
        return when (candidate.candidate.source) {
            LyricsSource.AMLL -> candidate.titleScore >= 0.76 &&
                candidate.artistScore != null && candidate.artistScore >= 0.68 && candidate.score >= 0.72
            else -> false
        }
    }
}
