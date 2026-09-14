package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import kotlin.math.pow

/** Reflows one translated block over primary line boundaries using display-oriented local heuristics. */
internal object TranslationComfortSegmenter {
    fun segment(text: String, primaryLines: List<LyricLine>): List<String>? {
        if (primaryLines.isEmpty()) return emptyList()
        val cleaned = text.replace(Regex("[\\r\\n]+"), " ").trim()
        if (cleaned.isEmpty()) return null
        if (primaryLines.size == 1) return listOf(cleaned)

        val boundaries = candidateBoundaries(cleaned)
        if (boundaries.size <= primaryLines.size) return null
        val weights = primaryLines.map { PerformanceAwareTextMatcher.effectiveWeight(it.text).toDouble() }
        val totalWeight = weights.sum()
        val totalMeasure = displayMeasure(cleaned).toDouble()
        val costs = Array(primaryLines.size + 1) { DoubleArray(boundaries.size) { INFINITY } }
        val previous = Array(primaryLines.size + 1) { IntArray(boundaries.size) { -1 } }
        costs[0][0] = 0.0

        for (part in primaryLines.indices) {
            for (startIndex in boundaries.indices) {
                val current = costs[part][startIndex]
                if (current == INFINITY) continue
                val remainingParts = primaryLines.size - part - 1
                for (endIndex in (startIndex + 1) until boundaries.size) {
                    if (boundaries.size - endIndex - 1 < remainingParts) break
                    val start = boundaries[startIndex]
                    val end = boundaries[endIndex]
                    val segment = cleaned.substring(start, end).trim()
                    if (segment.isEmpty() || segment.none(Character::isLetterOrDigit)) continue
                    val expected = totalMeasure * weights[part] / totalWeight
                    val actual = displayMeasure(segment).toDouble()
                    val cost = current + segmentCost(cleaned, start, end, actual, expected)
                    if (cost < costs[part + 1][endIndex]) {
                        costs[part + 1][endIndex] = cost
                        previous[part + 1][endIndex] = startIndex
                    }
                }
            }
        }

        var boundaryIndex = boundaries.lastIndex
        if (previous[primaryLines.size][boundaryIndex] < 0) return null
        val parts = ArrayDeque<String>()
        for (part in primaryLines.size downTo 1) {
            val startIndex = previous[part][boundaryIndex]
            parts.addFirst(cleaned.substring(boundaries[startIndex], boundaries[boundaryIndex]).trim())
            boundaryIndex = startIndex
        }
        return parts.toList().takeIf { it.size == primaryLines.size && it.all(String::isNotBlank) }
    }

    private fun candidateBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf(0)
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val next = offset + Character.charCount(codePoint)
            if (isCjk(codePoint) || Character.isWhitespace(codePoint) || isPunctuation(codePoint)) {
                boundaries += next
            } else if (next == text.length || startsBoundary(text, next)) {
                boundaries += next
            }
            offset = next
        }
        if (boundaries.last() != text.length) boundaries += text.length
        return boundaries.distinct()
    }

    private fun startsBoundary(text: String, offset: Int): Boolean {
        if (offset >= text.length) return true
        val next = text.codePointAt(offset)
        return Character.isWhitespace(next) || isPunctuation(next) || isCjk(next)
    }

    private fun segmentCost(
        text: String,
        start: Int,
        end: Int,
        actual: Double,
        expected: Double,
    ): Double {
        val ratioCost = ((actual - expected) / expected.coerceAtLeast(1.0)).pow(2) * 3.0
        val shortPenalty = if (actual <= 1.0 && expected > 1.5) 2.5 else 0.0
        val boundaryReward = if (end == text.length) 0.0 else when (text.codePointBefore(end)) {
            '.'.code, '!'.code, '?'.code, '。'.code, '！'.code, '？'.code -> -1.0
            ','.code, ';'.code, ':'.code, '，'.code, '；'.code, '：'.code, '、'.code -> -0.55
            else -> if (Character.isWhitespace(text.codePointBefore(end))) -0.25 else 0.18
        }
        val leadingPunctuationPenalty = if (start < text.length && isPunctuation(text.codePointAt(start))) 1.4 else 0.0
        return ratioCost + shortPenalty + boundaryReward + leadingPunctuationPenalty
    }

    private fun displayMeasure(value: String): Int {
        var measure = 0
        var inWord = false
        value.codePoints().forEach { codePoint ->
            when {
                isCjk(codePoint) -> {
                    measure++
                    inWord = false
                }
                Character.isLetterOrDigit(codePoint) -> {
                    if (!inWord) measure++
                    inWord = true
                }
                else -> inWord = false
            }
        }
        return measure.coerceAtLeast(1)
    }

    private fun isCjk(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }

    private fun isPunctuation(codePoint: Int): Boolean = Character.getType(codePoint) in PUNCTUATION_TYPES

    private val PUNCTUATION_TYPES = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
    )
    private const val INFINITY = 1.0e12
}
