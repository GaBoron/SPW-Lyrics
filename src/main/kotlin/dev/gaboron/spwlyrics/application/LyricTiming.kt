package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.*

/** Timing commands depend on media timestamps, never on a UI timer or playback implementation. */
internal object LyricTiming {
    fun cursors(lines: List<EditableLyricLine>, skipped: Set<Int> = emptySet()): List<TimingCursor> = lines
        .filter { it.id !in skipped }.flatMap { line -> line.words.indices.filter { line.words[it].text.isNotBlank() }.map { TimingCursor(line.id, it) } }

    fun mark(lines: List<EditableLyricLine>, progress: TimingProgress, mark: TimingMark, positionMs: Long,
        options: TimingOptions, skipped: Set<Int> = emptySet(), emptyBeats: Int = 0): TimingChange {
        val cursor = progress.cursor ?: error("请选择要打轴的词；没有逐字内容时请先分词。")
        val order = cursors(lines, skipped)
        val index = order.indexOf(cursor)
        require(index >= 0) { "当前词已变化，请重新选择。" }
        val eligible = order.filter { it.lineId == cursor.lineId }
        val first = cursor == eligible.first()
        val last = cursor == eligible.last()
        val time = (positionMs + options.offsetMs).coerceAtLeast(0)
        val changes = linkedMapOf<Int, EditableLyricLine>()
        fun update(target: TimingCursor, start: Long? = null, end: Long? = null) {
            val row = changes[target.lineId] ?: lines.first { it.id == target.lineId }
            val word = row.words[target.word]
            val from = start ?: parse(word.start)
            val to = end ?: maxOf(parse(word.end), from)
            require(to >= from) { "词尾早于词首，请先用 F 重新记录词首。" }
            val newStart = if (first && target == cursor && start != null) time else minOf(parse(row.start), from)
            val words = row.words.mapIndexed { i, old ->
                if (i == target.word) old.copy(start = format(from), end = format(to))
                else if (start != null && first && target == cursor) {
                    val anchored = maxOf(parse(old.start), newStart)
                    old.copy(start = format(anchored), end = format(maxOf(parse(old.end), anchored)))
                } else old
            }
            val rowEnd = words.maxOf { parse(it.end) }
            changes[row.id] = row.copy(start = format(newStart), end = format(rowEnd), words = words)
        }
        if (mark == TimingMark.START || mark == TimingMark.CONNECT && first && options.smartFirst && progress.firstStarted != cursor) {
            update(cursor, start = time)
            return TimingChange(changes, progress.copy(firstStarted = cursor, emptyBeat = 0))
        }
        if (mark == TimingMark.CONNECT && progress.emptyBeat < emptyBeats) {
            return TimingChange(emptyMap(), progress.copy(emptyBeat = progress.emptyBeat + 1))
        }
        update(cursor, end = time)
        val next = order.getOrNull(index + 1)
        val connect = mark == TimingMark.CONNECT && !(last && options.smartLast)
        if (connect && next != null) {
            // The next line may start at the same boundary; a smart tail intentionally leaves a gap.
            val nextRow = lines.first { it.id == next.lineId }
            val startWords = nextRow.words.mapIndexed { i, old ->
                val begin = if (i == next.word) time else if (next.lineId != cursor.lineId) maxOf(parse(old.start), time) else parse(old.start)
                old.copy(start = format(begin), end = format(maxOf(parse(old.end), begin)))
            }
            val currentRow = changes[next.lineId] ?: nextRow
            val merged = currentRow.words.mapIndexed { i, old -> if (i == next.word || next.lineId != cursor.lineId) startWords[i] else old }
            changes[next.lineId] = currentRow.copy(start = if (next.lineId != cursor.lineId) format(time) else currentRow.start,
                end = format(merged.maxOf { parse(it.end) }), words = merged)
        }
        return TimingChange(changes, TimingProgress(next, if (connect) next else null))
    }

    private fun parse(value: String): Long = dev.gaboron.spwlyrics.codec.LyricEditTime.parse(value) ?: 0L
    private fun format(value: Long) = dev.gaboron.spwlyrics.codec.LyricEditTime.format(value)
}
