package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsScriptConverterTest {
    @Test
    fun `converts lyric text words and translations to simplified Chinese`() {
        val document = LyricsDocument(
            LyricsSource.APPLE_MUSIC,
            LyricsFormat.TTML,
            listOf(
                LyricLine(
                    startMs = 1_000,
                    text = "繁體歌詞閃耀",
                    words = listOf(LyricWord(1_000, 2_000, "繁體歌詞")),
                    translation = "妳在這裡",
                ),
            ),
            metadata = mapOf("album" to listOf("傳統專輯")),
        )

        val converted = LyricsScriptConverter.toSimplifiedChinese(document)

        assertEquals("繁体歌词闪耀", converted.lines.single().text)
        assertEquals("繁体歌词", converted.lines.single().words.single().text)
        assertEquals("你在这里", converted.lines.single().translation)
        assertEquals(listOf("传统专辑"), converted.metadata["album"])
    }
}
