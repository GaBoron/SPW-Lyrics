package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.TimingCursor

internal data class PlayingTimingWords(val cursors: Set<TimingCursor> = emptySet(), val follow: TimingCursor? = null)

/** Parsed once per draft change. Playback follows valid word intervals, independently of the edit cursor. */
internal class TimingPlaybackIndex(lines: List<EditableLyricLine>) {
    private data class Interval(val cursor: TimingCursor, val start: Long, val end: Long, val background: Boolean)
    private val intervals = lines.flatMap { line ->
        line.words.mapIndexedNotNull { index, word ->
            val start = runCatching { LyricEditTime.parse(word.start) }.getOrNull()
            val end = runCatching { LyricEditTime.parse(word.end) }.getOrNull()
            if (word.text.isBlank() || start == null || end == null || end <= start) null
            else Interval(TimingCursor(line.id, index), start, end, line.background)
        }
    }.sortedBy { it.start }
    private val latestEnd = LongArray(intervals.size).also { ends ->
        intervals.forEachIndexed { index, interval -> ends[index] = maxOf(interval.end, ends.getOrElse(index - 1) { 0 }) }
    }

    fun at(positionMs: Long): PlayingTimingWords {
        var low = 0
        var high = intervals.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (intervals[middle].start <= positionMs) low = middle + 1 else high = middle
        }
        val active = mutableListOf<Interval>()
        var index = low - 1
        while (index >= 0 && latestEnd[index] > positionMs) {
            intervals[index].takeIf { it.end > positionMs }?.let(active::add)
            index--
        }
        // During a gap keep the nearest preceding word in view instead of jumping to the edit cursor.
        val follow = active.firstOrNull { !it.background } ?: active.firstOrNull() ?: intervals.getOrNull(low - 1)
        return PlayingTimingWords(active.mapTo(linkedSetOf()) { it.cursor }, follow?.cursor)
    }
}
