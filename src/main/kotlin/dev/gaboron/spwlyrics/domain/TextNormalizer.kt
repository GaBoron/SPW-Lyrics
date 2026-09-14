package dev.gaboron.spwlyrics.domain

import com.github.houbb.opencc4j.util.ZhConverterUtil
import java.text.Normalizer
import kotlin.math.max

object TextNormalizer {
    private val versionRegex = Regex(
        """(?i)(official\s*(video|audio|mv)|lyrics?\s*video|live|现场版?|remix|remaster(?:ed)?|acoustic|cover|instrumental|inst\.?|off\s*vocal|karaoke|伴奏|纯音乐|翻唱|完整版|radio\s*edit|sped\s*up|slowed)""",
    )
    private val bracketRegex = Regex("""[\[【(（].*?[\]】)）]""")
    private val punctuationRegex = Regex("""[^\p{L}\p{N}]+""")
    private val artistSplitRegex = Regex(
        """(?i)\s*(?:/|、|,|，|;|；|&|＆|\+|×|\||\bfeat(?:\.|\b)|\bft(?:\.|\b)|\bwith\b)\s*""",
    )

    fun normalize(value: String): String = ZhConverterUtil.toSimple(Normalizer.normalize(value, Normalizer.Form.NFKC))
        .lowercase()
        .replace('’', '\'')
        .replace(bracketRegex, " ")
        .replace(punctuationRegex, " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun compact(value: String): String = normalize(value).replace(" ", "")

    fun removeVersionNoise(value: String): String = versionRegex.replace(value, " ")
        .replace(Regex("\\s+"), " ")

    fun versionTokens(value: String): Set<String> = versionRegex.findAll(
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(),
    ).map { it.value.replace(Regex("\\s+"), " ") }.toSet()

    fun splitArtists(value: String): List<String> = value.split(artistSplitRegex)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(::compact)

    fun similarity(left: String?, right: String?): Double {
        if (left.isNullOrBlank() && right.isNullOrBlank()) return 1.0
        if (left.isNullOrBlank() || right.isNullOrBlank()) return 0.0
        return similarity(prepare(left), prepare(right))
    }

    fun prepare(value: String): PreparedText {
        val normalized = normalize(value)
        return PreparedText(
            normalized = normalized,
            compact = normalized.replace(" ", ""),
            tokens = normalized.split(' ').filter(String::isNotBlank).toSet(),
        )
    }

    fun similarity(left: PreparedText, right: PreparedText): Double {
        if (left.normalized == right.normalized || left.compact == right.compact) return 1.0

        val edit = 1.0 - levenshtein(left.compact, right.compact).toDouble() /
            max(left.compact.length, right.compact.length)
        val tokenDice = dice(left.tokens, right.tokens)
        val contains = if (left.compact.contains(right.compact) || right.compact.contains(left.compact)) {
            val short = minOf(left.compact.length, right.compact.length).toDouble()
            0.78 + 0.22 * short / max(left.compact.length, right.compact.length)
        } else {
            0.0
        }
        return maxOf(contains, edit * 0.75 + tokenDice * 0.25).coerceIn(0.0, 1.0)
    }

    /** Uses the edit-distance length bound to reject impossible matches without allocating its DP matrix. */
    fun couldReachSimilarity(left: PreparedText, right: PreparedText, minimum: Double): Boolean {
        if (left.normalized == right.normalized || left.compact == right.compact) return true
        val lengthBound = minOf(left.compact.length, right.compact.length).toDouble() /
            max(left.compact.length, right.compact.length)
        val tokenDice = dice(left.tokens, right.tokens)
        val contains = if (left.compact.contains(right.compact) || right.compact.contains(left.compact)) {
            0.78 + 0.22 * lengthBound
        } else {
            0.0
        }
        return maxOf(contains, lengthBound * 0.75 + tokenDice * 0.25) >= minimum
    }

    class PreparedText internal constructor(
        val normalized: String,
        val compact: String,
        val tokens: Set<String>,
    )

    private fun dice(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size
        return 2.0 * intersection / (left.size + right.size)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
