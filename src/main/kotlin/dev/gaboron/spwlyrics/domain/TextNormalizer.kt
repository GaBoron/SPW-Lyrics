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
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 1.0
        val compactA = a.replace(" ", "")
        val compactB = b.replace(" ", "")
        if (compactA == compactB) return 1.0

        val edit = 1.0 - levenshtein(compactA, compactB).toDouble() / max(compactA.length, compactB.length)
        val tokenDice = dice(a.split(' ').filter(String::isNotBlank), b.split(' ').filter(String::isNotBlank))
        val contains = if (compactA.contains(compactB) || compactB.contains(compactA)) {
            val short = minOf(compactA.length, compactB.length).toDouble()
            0.78 + 0.22 * short / max(compactA.length, compactB.length)
        } else {
            0.0
        }
        return maxOf(contains, edit * 0.75 + tokenDice * 0.25).coerceIn(0.0, 1.0)
    }

    private fun dice(left: List<String>, right: List<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.toSet().intersect(right.toSet()).size
        return 2.0 * intersection / (left.toSet().size + right.toSet().size)
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
