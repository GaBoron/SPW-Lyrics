package dev.gaboron.spwlyrics.application

import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceAwareTextMatcherTest {
    @Test
    fun `discounts hyphenated stutter and internal elongation`() {
        assertTrue(PerformanceAwareTextMatcher.similarity("I-I-I-I-I feeel", "I feel") >= 0.95)
        assertTrue(PerformanceAwareTextMatcher.similarity("loooove you", "love you") >= 0.95)
    }

    @Test
    fun `does not erase meaningful repeated words`() {
        assertTrue(PerformanceAwareTextMatcher.similarity("no no no", "no") < 0.9)
    }

    @Test
    fun `ignores unicode presentation noise`() {
        assertTrue(PerformanceAwareTextMatcher.similarity("Ｆｅｅｌ\u200B it！", "feel it") >= 0.95)
    }
}
