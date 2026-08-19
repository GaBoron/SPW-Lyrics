package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossSourceLyricsAlignerTest {
    @Test
    fun `uses text and order to survive extra and missing source lines`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "夜空を駆ける"),
            line(20_000, "君の声を聞いた"),
            line(30_000, "朝が来るまで"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(1_000, "作词 作曲"),
            line(10_300, "夜空を駆ける", "穿过夜空"),
            line(30_200, "朝が来るまで", "直到清晨来临"),
        )

        val alignment = CrossSourceLyricsAligner.align(primary, secondary)

        assertEquals(listOf(0, 2), alignment.matches.map(CrossSourceLineMatch::primaryIndex))
        assertEquals(listOf("穿过夜空", "直到清晨来临"), alignment.matches.map { it.secondaryLine.translation })
    }

    @Test
    fun `does not attach one half of a split source line`() {
        val primary = document(LyricsSource.APPLE_MUSIC, line(10_000, "Hello world"))
        val secondary = document(
            LyricsSource.QQ,
            line(9_900, "Hello", "你好"),
            line(10_500, "world", "世界"),
        )

        assertTrue(CrossSourceLyricsAligner.align(primary, secondary).matches.isEmpty())
    }

    @Test
    fun `rejects unrelated lyrics even when timestamps are identical`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(1_000, "First lyric line"),
            line(2_000, "Second lyric line"),
            line(3_000, "Third lyric line"),
            line(4_000, "Fourth lyric line"),
        )
        val secondary = document(
            LyricsSource.NETEASE,
            line(1_000, "完全不同的句子一", "错误一"),
            line(2_000, "完全不同的句子二", "错误二"),
            line(3_000, "完全不同的句子三", "错误三"),
            line(4_000, "完全不同的句子四", "错误四"),
        )

        val alignment = CrossSourceLyricsAligner.align(primary, secondary)

        assertTrue(alignment.matches.isEmpty())
        assertFalse(alignment.provesSameRecording)
    }

    @Test
    fun `exact lyric anchors prove a recording despite a global timing offset`() {
        val texts = listOf("夜に駆ける", "沈むように", "溶けてゆくように", "二人だけの空")
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            *texts.mapIndexed { index, text -> line(index * 5_000L, text) }.toTypedArray(),
        )
        val secondary = document(
            LyricsSource.QQ,
            *texts.mapIndexed { index, text -> line(index * 5_000L + 2_500, text, "翻译 $index") }.toTypedArray(),
        )

        assertTrue(CrossSourceLyricsAligner.align(primary, secondary).provesSameRecording)
    }

    private fun line(startMs: Long, text: String, translation: String? = null) =
        LyricLine(startMs = startMs, text = text, translation = translation)

    private fun document(source: LyricsSource, vararg lines: LyricLine) =
        LyricsDocument(source, LyricsFormat.LRC, lines.toList())
}
