package dev.gaboron.spwlyrics.domain

/** Classifies parsed timing units without guessing whether each unit is a word or character. */
internal object LyricsGranularityClassifier {
    fun classify(lines: List<LyricLine>): LyricsQuality {
        val primary = lines.filter { !it.background && it.text.isNotBlank() }
        if (primary.isEmpty()) return LyricsQuality.PLAIN
        val timed = primary.filter { it.startMs != null }
        if (timed.isEmpty()) return LyricsQuality.PLAIN
        val wordTimed = timed.filter(::hasValidWordTiming)
        if (wordTimed.size * 10 < timed.size * MIN_TIMED_COVERAGE_TENTHS) return LyricsQuality.LINE_SYNCED

        return LyricsQuality.KARAOKE_SYNCED
    }

    private fun hasValidWordTiming(line: LyricLine): Boolean =
        line.words.isNotEmpty() &&
            line.words.all { it.startMs <= it.endMs } &&
            line.words.zipWithNext().all { (left, right) ->
                left.startMs <= right.startMs && left.endMs <= right.endMs
            }

    private const val MIN_TIMED_COVERAGE_TENTHS = 8
}
