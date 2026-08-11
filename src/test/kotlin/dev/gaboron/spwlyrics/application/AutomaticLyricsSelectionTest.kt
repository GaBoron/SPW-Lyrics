package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutomaticLyricsSelectionTest {
    @Test
    fun `word synced lyrics win over an earlier line synced source`() {
        val selection = AutomaticLyricsSelection()

        assertNull(selection.consider(fetched(LyricsSource.AMLL, wordSynced = false)))
        val winner = selection.consider(fetched(LyricsSource.QQ, wordSynced = true))

        assertEquals(LyricsSource.QQ, winner?.candidate?.source)
    }

    @Test
    fun `highest priority line synced lyrics are retained when no word lyrics exist`() {
        val selection = AutomaticLyricsSelection()

        selection.consider(fetched(LyricsSource.APPLE_MUSIC, wordSynced = false))
        selection.consider(fetched(LyricsSource.NETEASE, wordSynced = false))

        assertEquals(LyricsSource.APPLE_MUSIC, selection.fallback()?.candidate?.source)
    }

    private fun fetched(source: LyricsSource, wordSynced: Boolean): FetchedLyrics {
        val words = if (wordSynced) listOf(LyricWord(1_000, 2_000, "lyrics")) else emptyList()
        return FetchedLyrics(
            LyricsCandidate(source, source.name, "Song", listOf("Artist")),
            LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "lyrics", words))),
        )
    }
}
