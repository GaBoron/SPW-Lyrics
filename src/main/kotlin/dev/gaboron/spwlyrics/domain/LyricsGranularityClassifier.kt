package dev.gaboron.spwlyrics.domain

/** Classifies parsed timing units instead of trusting a provider's format label. */
internal object LyricsGranularityClassifier {
    fun classify(lines: List<LyricLine>, metadata: Map<String, List<String>>): LyricsQuality {
        val primary = lines.filter { !it.background && it.text.isNotBlank() }
        if (primary.isEmpty()) return LyricsQuality.PLAIN
        val timed = primary.filter { it.startMs != null }
        if (timed.isEmpty()) return LyricsQuality.PLAIN
        val wordTimed = timed.filter(::hasValidWordTiming)
        if (wordTimed.size * 10 < timed.size * MIN_TIMED_COVERAGE_TENTHS) return LyricsQuality.LINE_SYNCED

        when (metadata[TIMING_GRANULARITY_KEY]?.firstOrNull()?.lowercase()) {
            CHARACTER_METADATA_VALUE, "syllable" -> return LyricsQuality.CHARACTER_SYNCED
            WORD_METADATA_VALUE -> return LyricsQuality.WORD_SYNCED
        }
        return if (isCharacterGranularity(wordTimed)) {
            LyricsQuality.CHARACTER_SYNCED
        } else {
            LyricsQuality.WORD_SYNCED
        }
    }

    private fun hasValidWordTiming(line: LyricLine): Boolean =
        line.words.isNotEmpty() &&
            line.words.all { it.startMs <= it.endMs } &&
            line.words.zipWithNext().all { (left, right) ->
                left.startMs <= right.startMs && left.endMs <= right.endMs
            }

    private fun isCharacterGranularity(lines: List<LyricLine>): Boolean {
        val cjkCharacters = lines.sumOf { countCjk(it.text) }
        val latinWords = lines.sumOf { countLatinWords(it.text) }
        if (cjkCharacters >= latinWords && cjkCharacters > 0) {
            val individuallyTimed = lines.sumOf { line ->
                line.words.sumOf { word -> countCjk(word.text).takeIf { it == 1 } ?: 0 }
            }
            return individuallyTimed * 10 >= cjkCharacters * MIN_CHARACTER_COVERAGE_TENTHS
        }
        if (latinWords == 0) return false
        val timedLatinUnits = lines.sumOf { line -> line.words.count { LATIN_WORD.containsMatchIn(it.text) } }
        val requiredExtraUnits = maxOf(MIN_EXTRA_LATIN_UNITS, (latinWords + 9) / 10)
        return timedLatinUnits >= latinWords + requiredExtraUnits
    }

    private fun countCjk(value: String): Int = value.codePoints().filter(::isCjk).count().toInt()

    private fun countLatinWords(value: String): Int = LATIN_WORD.findAll(value).count()

    private fun isCjk(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }

    const val TIMING_GRANULARITY_KEY = "timingGranularity"
    const val CHARACTER_METADATA_VALUE = "character"
    const val WORD_METADATA_VALUE = "word"
    private const val MIN_TIMED_COVERAGE_TENTHS = 8
    private const val MIN_CHARACTER_COVERAGE_TENTHS = 8
    private const val MIN_EXTRA_LATIN_UNITS = 2
    private val LATIN_WORD = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
}
