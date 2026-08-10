package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            "[00:00.000]歌词来源：AMLL TTML DB[00:01.000]\n" +
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
            "[00:00.000]歌词来源：AMLL TTML DB[00:01.000]\n" +
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

    @Test
    fun `keeps duet lines concurrent without making the second line a translation`() {
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.TTML,
            lines = listOf(
                LyricLine(1_000, 2_000, "男声", agent = "v1"),
                LyricLine(1_000, 2_000, "女声", agent = "v2"),
            ),
        )

        val encoded = SpwLyricsEncoder.encode(document)

        assertEquals(1, Regex("(?m)^\\[00:01\\.000]").findAll(encoded).count())
        assertEquals(1, Regex("(?m)^\\[00:01\\.001]").findAll(encoded).count())
        assertTrue(encoded.contains("[00:01.001]女声[00:02.000]"))
    }

    @Test
    fun `keeps each translation attached to its concurrent main line`() {
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.TTML,
            lines = listOf(
                LyricLine(1_000, 2_000, "男声", translation = "Male", agent = "v1"),
                LyricLine(1_000, 2_000, "女声", translation = "Female", agent = "v2"),
            ),
        )

        assertEquals(
            "[00:00.000]歌词来源：AMLL TTML DB[00:01.000]\n" +
                "[00:01.000]男声[00:02.000]\n[00:01.000]Male\n" +
                "[00:01.001]女声[00:02.000]\n[00:01.001]Female",
            SpwLyricsEncoder.encode(document),
        )
    }

    @Test
    fun `untimed auxiliary lines do not degrade timed lyrics`() {
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.TTML,
            lines = listOf(
                LyricLine(text = "无时间说明"),
                LyricLine(
                    startMs = 1_000,
                    endMs = 2_000,
                    text = "逐字歌词",
                    words = listOf(
                        LyricWord(1_000, 1_500, "逐字"),
                        LyricWord(1_500, 2_000, "歌词"),
                    ),
                ),
            ),
        )

        assertEquals(
            "[00:00.000]歌词来源：AMLL TTML DB[00:01.000]\n" +
                "[00:01.000]逐字<00:01.500>歌词[00:02.000]",
            SpwLyricsEncoder.encode(document),
        )
    }
}
