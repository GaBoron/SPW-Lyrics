package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.*

/** Independent word ranges may overlap; editing one never moves a neighbouring voice or word. */
internal object TimingRangeEdit {
    fun change(lines: List<EditableLyricLine>, cursor: TimingCursor, start: Long, end: Long): EditableLyricLine {
        require(start >= 0 && end > start) { "词尾必须晚于词首。" }
        val line = lines.first { it.id == cursor.lineId }
        require(cursor.word in line.words.indices)
        val words = line.words.mapIndexed { index, word ->
            if (index == cursor.word) word.copy(start = LyricEditTime.format(start), end = LyricEditTime.format(end)) else word
        }
        val audible = words.filter { it.text.isNotBlank() }
        return line.copy(words = words, start = LyricEditTime.format(audible.minOf { LyricEditTime.parse(it.start) ?: 0 }),
            end = LyricEditTime.format(audible.maxOf { LyricEditTime.parse(it.end) ?: 0 }))
    }
}
