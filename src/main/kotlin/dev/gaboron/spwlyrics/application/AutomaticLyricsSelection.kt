package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality

internal data class FetchedLyrics(
    val candidate: LyricsCandidate,
    val document: LyricsDocument,
)

/** Keeps the highest-priority fallback while every source is checked for word-synced lyrics. */
internal class AutomaticLyricsSelection {
    private var lineSynced: FetchedLyrics? = null
    private var plain: FetchedLyrics? = null

    fun consider(fetched: FetchedLyrics): FetchedLyrics? = when (fetched.document.quality) {
        LyricsQuality.WORD_SYNCED -> fetched
        LyricsQuality.LINE_SYNCED -> null.also { if (lineSynced == null) lineSynced = fetched }
        LyricsQuality.PLAIN -> null.also { if (plain == null) plain = fetched }
    }

    fun fallback(): FetchedLyrics? = lineSynced ?: plain
}
