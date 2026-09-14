package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TextNormalizer
import java.text.Normalizer

/** Normalizes performance notation for matching without changing the displayed lyric text. */
internal object PerformanceAwareTextMatcher {
    fun similarity(left: String, right: String): Double {
        return similarity(prepare(left), prepare(right))
    }

    fun prepare(value: String): PreparedText {
        val preparedVariants = variants(value).map(TextNormalizer::prepare)
        return PreparedText(preparedVariants, preparedVariants.first().compact)
    }

    fun similarity(left: PreparedText, right: PreparedText): Double =
        left.variants.maxOf { leftVariant ->
            right.variants.maxOf { rightVariant -> TextNormalizer.similarity(leftVariant, rightVariant) }
        }

    fun couldReachSimilarity(left: PreparedText, right: PreparedText, minimum: Double): Boolean =
        left.variants.any { leftVariant ->
            right.variants.any { rightVariant ->
                TextNormalizer.couldReachSimilarity(leftVariant, rightVariant, minimum)
            }
        }

    fun signature(value: String): String = prepare(value).signature

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

    class PreparedText internal constructor(
        internal val variants: List<TextNormalizer.PreparedText>,
        val signature: String,
    )
}
