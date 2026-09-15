package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Owns automatic ranking: parsed timing granularity first, provider priority second. */
internal object LyricsSelectionPolicy {
    fun select(candidates: List<FetchedLyrics>): FetchedLyrics? = candidates.minWithOrNull(
        compareByDescending<FetchedLyrics> { it.document.quality.rank }
            .thenBy { it.candidate.source.priority },
    )

    fun canReplace(current: LyricsDocument, replacement: LyricsDocument): Boolean =
        replacement.quality.rank > current.quality.rank ||
            replacement.quality == current.quality && replacement.source.priority <= current.source.priority
}
