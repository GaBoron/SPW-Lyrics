package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import java.text.Normalizer

/** Infer secondary text only when matching timestamps and scripts provide evidence. */
internal object TimedSecondaryLyrics {
    private val romajiWord = Regex("(?:(?:[kstnhmyrwgzdbp]|sh|ch|ts|ky|ny|ry|gy|by|py|j|f)?[aeiou]|n|'|[kstp])+")
    private val japaneseHints = setOf("wa", "wo", "ga", "ni", "no", "de", "to", "kimi", "boku", "watashi", "anata", "kono", "sono")
    fun merge(lines: List<LyricLine>): List<LyricLine> = lines.groupBy { it.startMs }.values.flatMap { group ->
        val primary = group.firstOrNull { it.words.isNotEmpty() } ?: group.first()
        var merged = primary
        val remaining = mutableListOf<LyricLine>()
        val primaryScript = script(primary.text)
        group.filter { it !== primary }.forEach { line ->
            val candidate = script(line.text)
            val roman = candidate == "latin" &&
                (primaryScript == "ja" && looksJapanese(line.text) || primaryScript == "zh" && looksPinyin(line.text) ||
                    primaryScript == "ko" && Regex("(?i)\\b(sarang|saranghae|neoreul|naneun|geudae|uri|nae|eodi|eotteoke)\\b").containsMatchIn(line.text))
            when {
                line.words.isNotEmpty() || candidate == primaryScript -> remaining += line
                roman && merged.romanization == null -> merged = merged.copy(romanization = line.text)
                candidate != "other" && merged.translation == null -> merged = merged.copy(translation = line.text)
                else -> remaining += line
            }
        }
        listOf(merged) + remaining
    }

    private fun script(text: String): String = when {
        text.any { it in '\u3040'..'\u30ff' } -> "ja"
        text.any { it in '\uac00'..'\ud7af' || it in '\u1100'..'\u11ff' } -> "ko"
        text.any { it in '\u3400'..'\u9fff' } -> "zh"
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } -> "latin"
        else -> "other"
    }

    private fun looksPinyin(text: String): Boolean = text.any { it in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ" } ||
        Regex("(?i)\\b[a-zü]+[1-5]\\b").containsMatchIn(text)

    private fun looksJapanese(text: String): Boolean {
        val words = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .split(Regex("[^a-z']+")).filter(String::isNotBlank)
        return words.size >= 2 && words.count { romajiWord.matches(it) } >= words.size * .8 && words.any { it in japaneseHints }
    }
}
