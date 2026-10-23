package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsPreferences

/** Uses parsed timing granularity and the user's selection order. */
internal object LyricsSelectionPolicy {
    fun select(candidates: List<FetchedLyrics>, preferences: LyricsPreferences): FetchedLyrics? = candidates.filter {
        it.document.quality in preferences.qualities
    }.minWithOrNull(
        Comparator { left, right -> preferences.compare(left.candidate.source, left.document.quality, right.candidate.source, right.document.quality) },
    )

    fun canReplace(current: LyricsDocument, replacement: LyricsDocument, preferences: LyricsPreferences): Boolean =
        preferences.compare(replacement.source, replacement.quality, current.source, current.quality) <= 0
}
