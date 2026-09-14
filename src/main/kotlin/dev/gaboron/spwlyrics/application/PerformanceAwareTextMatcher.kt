package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TextNormalizer
import java.text.Normalizer

/** Normalizes performance notation for matching without changing the displayed lyric text. */
internal object PerformanceAwareTextMatcher {
    fun similarity(left: String, right: String): Double {
        val leftVariants = variants(left)
        val rightVariants = variants(right)
        return leftVariants.maxOf { leftVariant ->
            rightVariants.maxOf { rightVariant -> TextNormalizer.similarity(leftVariant, rightVariant) }
        }
    }

    fun signature(value: String): String = TextNormalizer.compact(variants(value).first())

    fun effectiveWeight(value: String): Int = tokens(variants(value).first()).sumOf { token ->
        if (token.any(::isCjk)) token.codePointCount(0, token.length) else 1
    }.coerceAtLeast(1)

    private fun variants(value: String): List<String> {
        val prepared = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(ZERO_WIDTH, "")
            .replace(STUTTERED_CHARACTER) { it.groupValues[1] }
        return listOf(
            collapseInternalRuns(prepared, 2),
            collapseInternalRuns(prepared, 1),
        ).distinct()
    }

    private fun collapseInternalRuns(value: String, maximum: Int): String = buildString {
        var previous = -1
        var runLength = 0
        value.codePoints().forEach { codePoint ->
            if (codePoint == previous && Character.isLetterOrDigit(codePoint)) {
                runLength++
            } else {
                previous = codePoint
                runLength = 1
            }
            if (runLength <= maximum) appendCodePoint(codePoint)
        }
    }

    private fun tokens(value: String): List<String> = TextNormalizer.normalize(value)
        .split(' ')
        .filter(String::isNotBlank)

    private fun isCjk(character: Char): Boolean = when (Character.UnicodeScript.of(character.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }

    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")
    private val STUTTERED_CHARACTER = Regex("(?iu)([\\p{L}\\p{N}])(?:[-‐‑‒–—]\\1){2,}")
}
