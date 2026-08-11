package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecondaryLyricsEnricherTest {
    @Test
    fun `continues supplementing when translation coverage is incomplete`() {
        assertTrue(SecondaryLyricsEnricher.needsTranslation(document(translatedLines = 3)))
    }

    @Test
    fun `stops supplementing after translation coverage reaches eighty percent`() {
        assertFalse(SecondaryLyricsEnricher.needsTranslation(document(translatedLines = 4)))
    }

    private fun document(translatedLines: Int) = LyricsDocument(
        source = LyricsSource.APPLE_MUSIC,
        format = LyricsFormat.TTML,
        lines = List(5) { index ->
            LyricLine(
                startMs = index * 1_000L,
                text = "Line $index",
                translation = if (index < translatedLines) "翻译 $index" else null,
            )
        },
    )
}
