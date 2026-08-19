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

        assertEquals(listOf(listOf(0), listOf(2)), alignment.groups.map(CrossSourceAlignmentGroup::primaryIndices))
        assertEquals(
            listOf("穿过夜空", "直到清晨来临"),
            alignment.groups.map { it.secondaryLines.single().translation },
        )
    }

    @Test
    fun `groups multiple source lines that compose one AM line`() {
        val primary = document(LyricsSource.APPLE_MUSIC, line(10_000, "I have, I have an apple"))
        val secondary = document(
            LyricsSource.QQ,
            line(9_900, "I have", "我有"),
            line(10_500, "I have an apple", "我有一个苹果"),
        )

        val group = CrossSourceLyricsAligner.align(primary, secondary).groups.single()

        assertEquals(CrossSourceAlignmentRelation.ONE_TO_MANY, group.relation)
        assertEquals(listOf(0), group.primaryIndices)
        assertEquals(listOf(0, 1), group.secondaryIndices)
    }

    @Test
    fun `groups multiple AM lines that compose one source line`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have"),
            line(10_500, "I have an apple"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(9_900, "I have, I have an apple", "我有，我有一个苹果"),
        )

        val group = CrossSourceLyricsAligner.align(primary, secondary).groups.single()

        assertEquals(CrossSourceAlignmentRelation.MANY_TO_ONE, group.relation)
        assertEquals(listOf(0, 1), group.primaryIndices)
        assertEquals(listOf(0), group.secondaryIndices)
    }

    @Test
    fun `prefers minimal one-to-one groups when line boundaries already agree`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have"),
            line(20_000, "I have an apple"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_100, "I have", "我有"),
            line(20_100, "I have an apple", "我有一个苹果"),
        )

        val alignment = CrossSourceLyricsAligner.align(primary, secondary)

        assertEquals(2, alignment.groups.size)
        assertTrue(alignment.groups.all { it.relation == CrossSourceAlignmentRelation.ONE_TO_ONE })
    }

    @Test
    fun `supports three source lines composing one AM line`() {
        val primary = document(LyricsSource.APPLE_MUSIC, line(10_000, "A, B, C"))
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "A", "甲"),
            line(10_500, "B", "乙"),
            line(11_000, "C", "丙"),
        )

        val group = CrossSourceLyricsAligner.align(primary, secondary).groups.single()

        assertEquals(CrossSourceAlignmentRelation.ONE_TO_MANY, group.relation)
        assertEquals(3, group.secondaryIndices.size)
    }

    @Test
    fun `does not group lines across different AM agents`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "Hello", agent = "v1"),
            line(10_500, "world", agent = "v2"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "Hello world", "你好，世界"),
        )

        assertTrue(CrossSourceLyricsAligner.align(primary, secondary).groups.isEmpty())
    }

    @Test
    fun `keeps repeated lyrics in monotonic order around a missing line`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "Verse one"),
            line(20_000, "Same chorus"),
            line(30_000, "Bridge line"),
            line(40_000, "Same chorus"),
        )
        val secondary = document(
            LyricsSource.NETEASE,
            line(10_100, "Verse one", "主歌"),
            line(20_100, "Same chorus", "副歌一"),
            line(40_100, "Same chorus", "副歌二"),
        )

        val alignment = CrossSourceLyricsAligner.align(primary, secondary)

        assertEquals(listOf(0, 1, 3), alignment.groups.flatMap(CrossSourceAlignmentGroup::primaryIndices))
        assertEquals(listOf(0, 1, 2), alignment.groups.flatMap(CrossSourceAlignmentGroup::secondaryIndices))
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

        assertTrue(alignment.groups.isEmpty())
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

    private fun line(startMs: Long, text: String, translation: String? = null, agent: String? = null) =
        LyricLine(startMs = startMs, text = text, translation = translation, agent = agent)

    private fun document(source: LyricsSource, vararg lines: LyricLine) =
        LyricsDocument(source, LyricsFormat.LRC, lines.toList())
}
