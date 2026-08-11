package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.MatchEngine

/** Accepts the same recording across platform-specific artist aliases when only secondary text is consumed. */
internal object TranslationMatchPolicy {
    fun accepts(candidate: CandidateScore): Boolean {
        if (candidate.passesAutomaticGate) return true
        if (candidate.versionConflict) return false
        if (candidate.durationScore != null && candidate.durationScore < MatchEngine.MIN_DURATION) return false
        return candidate.titleScore >= MatchEngine.STRONG_TITLE &&
            candidate.durationScore != null && candidate.durationScore >= 0.85
    }
}
