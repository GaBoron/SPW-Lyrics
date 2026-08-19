package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationProjectionPolicyTest {
    @Test
    fun `combines complete split translations for one AM line`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have, I have an apple"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "I have", "我有"),
            line(10_500, "I have an apple", "我有一个苹果"),
        )

        val projection = project(primary, secondary)

        assertEquals(mapOf(0 to "我有，我有一个苹果"), projection.translations)
    }

    @Test
    fun `splits an explicitly segmented translation across AM lines`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have"),
            line(10_500, "I have an apple"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "I have, I have an apple", "我有，我有一个苹果"),
        )

        val projection = project(primary, secondary)

        assertEquals(mapOf(0 to "我有", 1 to "我有一个苹果"), projection.translations)
    }

    @Test
    fun `does not guess how to split an indivisible translation`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have"),
            line(10_500, "I have an apple"),
        )
        val secondary = document(
            LyricsSource.NETEASE,
            line(10_000, "I have, I have an apple", "我拥有一个苹果"),
        )

        assertTrue(project(primary, secondary).translations.isEmpty())
    }

    @Test
    fun `does not combine an incomplete set of split translations`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "I have, I have an apple"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "I have", "我有"),
            line(10_500, "I have an apple"),
        )

        assertTrue(project(primary, secondary).translations.isEmpty())
    }

    @Test
    fun `combines three complete translations in source order`() {
        val primary = document(LyricsSource.APPLE_MUSIC, line(10_000, "A, B, C"))
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "A", "甲"),
            line(10_500, "B", "乙"),
            line(11_000, "C", "丙"),
        )

        assertEquals(mapOf(0 to "甲，乙，丙"), project(primary, secondary).translations)
    }

    @Test
    fun `splits translations separated by line breaks`() {
        val primary = document(
            LyricsSource.APPLE_MUSIC,
            line(10_000, "A"),
            line(10_500, "B"),
            line(11_000, "C"),
        )
        val secondary = document(
            LyricsSource.QQ,
            line(10_000, "A, B, C", "甲\n乙\n丙"),
        )

        assertEquals(
            mapOf(0 to "甲", 1 to "乙", 2 to "丙"),
            project(primary, secondary).translations,
        )
    }

    @Test
    fun `keeps pairwise translations when a many-to-many group is internally reliable`() {
        val group = CrossSourceAlignmentGroup(
            primaryIndices = listOf(0, 1),
            secondaryIndices = listOf(0, 1),
            primaryLines = listOf(line(10_000, "Hello"), line(20_000, "world")),
            secondaryLines = listOf(line(10_000, "Hello", "你好"), line(20_000, "world", "世界")),
            textSimilarity = 1.0,
            timingScore = 1.0,
            confidence = 1.0,
            matchedCharacterCount = 10,
        )
        val alignment = CrossSourceAlignment(
            groups = listOf(group),
            primaryLineCount = 2,
            secondaryLineCount = 2,
            primaryCharacterCount = 10,
            secondaryCharacterCount = 10,
        )

        assertEquals(
            mapOf(0 to "你好", 1 to "世界"),
            TranslationProjectionPolicy.project(alignment).translations,
        )
    }

    private fun project(primary: LyricsDocument, secondary: LyricsDocument) =
        TranslationProjectionPolicy.project(CrossSourceLyricsAligner.align(primary, secondary))

    private fun line(startMs: Long, text: String, translation: String? = null) =
        LyricLine(startMs = startMs, text = text, translation = translation)

    private fun document(source: LyricsSource, vararg lines: LyricLine) =
        LyricsDocument(source, LyricsFormat.LRC, lines.toList())
}
