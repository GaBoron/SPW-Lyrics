package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationComfortSegmenterTest {
    @Test
    fun `prefers a natural punctuation boundary`() {
        val parts = TranslationComfortSegmenter.segment(
            "抬起头，看天空",
            listOf(LyricLine(text = "look up"), LyricLine(text = "at the sky")),
        )!!

        assertEquals(listOf("抬起头，", "看天空"), parts)
    }

    @Test
    fun `keeps every line nonblank when no punctuation exists`() {
        val original = "我不知道你为什么在今晚独自离我而去"
        val parts = TranslationComfortSegmenter.segment(
            original,
            listOf(
                LyricLine(text = "I don't know"),
                LyricLine(text = "why you left me"),
                LyricLine(text = "alone tonight"),
            ),
        )!!

        assertEquals(3, parts.size)
        assertEquals(original, parts.joinToString(""))
        assertTrue(parts.all(String::isNotBlank))
    }
}
