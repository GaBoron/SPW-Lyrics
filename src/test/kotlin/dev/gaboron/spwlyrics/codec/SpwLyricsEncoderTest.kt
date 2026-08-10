package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SpwLyricsEncoderTest {
    @Test
    fun `encodes word timing and prefers translation`() {
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.TTML,
            lines = listOf(
                LyricLine(
                    startMs = 1_000,
                    endMs = 2_000,
                    text = "你好",
                    words = listOf(
                        LyricWord(1_000, 1_500, "你"),
                        LyricWord(1_500, 2_000, "好"),
                    ),
                    translation = "Hello",
                    romanization = "ni hao",
                ),
            ),
        )

        assertEquals(
            "[00:00.000]歌词来源：AMLL TTML DB\n" +
                "[00:01.000]你<00:01.500>好[00:02.000]\n[00:01.000]Hello",
            SpwLyricsEncoder.encode(document),
        )
    }

    @Test
    fun `preserves untimed spaces between amll word tokens`() {
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.TTML,
            lines = listOf(
                LyricLine(
                    startMs = 1_000,
                    endMs = 3_000,
                    text = "Still we clash",
                    words = listOf(
                        LyricWord(1_000, 1_500, "Still"),
                        LyricWord(1_500, 2_000, "we"),
                        LyricWord(2_000, 3_000, "clash"),
                    ),
                ),
            ),
        )

        assertEquals(
            "[00:00.000]歌词来源：AMLL TTML DB\n" +
                "[00:01.000]Still <00:01.500>we <00:02.000>clash[00:03.000]",
            SpwLyricsEncoder.encode(document),
        )
    }

    @Test
    fun `adds source as first line for plain lyrics`() {
        val document = LyricsDocument(
            source = LyricsSource.QQ,
            format = LyricsFormat.PLAIN,
            lines = listOf(LyricLine(text = "Hello")),
        )

        assertEquals("歌词来源：QQ音乐\nHello", SpwLyricsEncoder.encode(document))
    }
}
