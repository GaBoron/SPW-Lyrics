package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `adds only translations whose source lyric line is aligned`() {
        val primary = LyricsDocument(
            LyricsSource.APPLE_MUSIC,
            LyricsFormat.TTML,
            listOf(
                LyricLine(10_000, text = "夜空を駆ける"),
                LyricLine(20_000, text = "君の声を聞いた"),
                LyricLine(30_000, text = "朝が来るまで"),
            ),
        )
        val secondary = LyricsDocument(
            LyricsSource.QQ,
            LyricsFormat.QRC,
            listOf(
                LyricLine(10_200, text = "夜空を駆ける", translation = "穿过夜空"),
                LyricLine(20_000, text = "不属于这首歌", translation = "错误翻译"),
                LyricLine(30_100, text = "朝が来るまで", translation = "直到清晨来临"),
            ),
        )

        val enriched = SecondaryLyricsEnricher.enrich(primary, secondary)

        assertEquals("穿过夜空", enriched.lines[0].translation)
        assertNull(enriched.lines[1].translation)
        assertEquals("直到清晨来临", enriched.lines[2].translation)
        assertEquals(listOf("QQ音乐"), enriched.metadata[SecondaryLyricsEnricher.TRANSLATION_SOURCE_KEY])
    }

    @Test
    fun `combines split source translations without changing AM line timing`() {
        val primary = LyricsDocument(
            LyricsSource.APPLE_MUSIC,
            LyricsFormat.TTML,
            listOf(LyricLine(10_000, 12_000, "I have, I have an apple")),
        )
        val secondary = LyricsDocument(
            LyricsSource.QQ,
            LyricsFormat.QRC,
            listOf(
                LyricLine(10_000, 10_500, "I have", translation = "我有"),
                LyricLine(10_500, 12_000, "I have an apple", translation = "我有一个苹果"),
            ),
        )

        val enriched = SecondaryLyricsEnricher.enrich(primary, secondary)

        assertEquals("我有，我有一个苹果", enriched.lines.single().translation)
        assertEquals(10_000, enriched.lines.single().startMs)
        assertEquals(12_000, enriched.lines.single().endMs)
    }

    @Test
    fun `splits an explicit combined translation across AM lines`() {
        val primary = LyricsDocument(
            LyricsSource.APPLE_MUSIC,
            LyricsFormat.TTML,
            listOf(
                LyricLine(10_000, 10_500, "I have"),
                LyricLine(10_500, 12_000, "I have an apple"),
            ),
        )
        val secondary = LyricsDocument(
            LyricsSource.QQ,
            LyricsFormat.QRC,
            listOf(LyricLine(10_000, 12_000, "I have, I have an apple", translation = "我有，我有一个苹果")),
        )

        val enriched = SecondaryLyricsEnricher.enrich(primary, secondary)

        assertEquals(listOf("我有", "我有一个苹果"), enriched.lines.map(LyricLine::translation))
    }

    @Test
    fun `leaves AM lines untranslated when a combined translation cannot be split`() {
        val primary = LyricsDocument(
            LyricsSource.APPLE_MUSIC,
            LyricsFormat.TTML,
            listOf(
                LyricLine(10_000, 10_500, "I have"),
                LyricLine(10_500, 12_000, "I have an apple"),
            ),
        )
        val secondary = LyricsDocument(
            LyricsSource.NETEASE,
            LyricsFormat.LRC,
            listOf(LyricLine(10_000, 12_000, "I have, I have an apple", translation = "我拥有一个苹果")),
        )

        val enriched = SecondaryLyricsEnricher.enrich(primary, secondary)

        assertTrue(enriched.lines.all { it.translation == null })
        assertNull(enriched.metadata[SecondaryLyricsEnricher.TRANSLATION_SOURCE_KEY])
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
