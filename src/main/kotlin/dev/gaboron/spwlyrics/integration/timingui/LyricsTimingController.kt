package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.application.*
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorController
import java.lang.reflect.InvocationTargetException
import java.util.ArrayDeque

/** Coordinates the pure timing rules, draft edits and host transport; it owns no audio player. */
internal class LyricsTimingController(private val editor: MetadataEditorController, private val transport: TimingPlayback) {
    var state by mutableStateOf(TimingEditorState()); private set
    var playback by mutableStateOf<TimingPlaybackSnapshot?>(null); private set
    private var loaded: Any? = null
    private var raw: String? = null
    private var observedEdits: Any? = null
    private var retryAfter = 0L
    private data class Press(val snapshot: TimingPlaybackSnapshot, val cursor: TimingCursor?, val at: Long)
    private val pressing = mutableMapOf<TimingMark, Press>()
    private data class Edit(val before: Map<Int, EditableLyricLine>, val after: Map<Int, EditableLyricLine>,
        val previous: TimingProgress, val next: TimingProgress)
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()

    fun poll() {
        syncDraft()
        val path = editor.state.loaded?.path ?: return
        if (System.nanoTime() < retryAfter) return
        try {
            playback = transport.read(path)
            state = state.copy(available = true, error = if (!state.available) false else state.error,
                message = if (!state.available) "已连接宿主。F 词首 · G 连续轴 · H 词尾；可用空格播放/暂停。" else state.message)
        } catch (error: Exception) {
            playback = null
            pressing.clear()
            retryAfter = System.nanoTime() + 1_000_000_000
            fail(error)
            state = state.copy(available = false)
        }
    }

    private fun syncDraft() {
        val current = editor.state
        val base = current.structured
        if (loaded !== current.loaded || raw != base?.raw || observedEdits !== current.lineEdits) {
            loaded = current.loaded; raw = base?.raw; observedEdits = current.lineEdits
            undo.clear(); redo.clear(); pressing.clear()
            val lines = base?.lines?.map { current.line(it.id) ?: it }.orEmpty()
            val cursor = LyricTiming.cursors(lines).firstOrNull { it.lineId == current.selectedLineId }
                ?: LyricTiming.cursors(lines).firstOrNull()
            state = state.copy(progress = TimingProgress(cursor), skipped = emptySet(), emptyBeats = emptyMap())
        }
        state = state.copy(lines = base?.lines?.map { current.line(it.id) ?: it }.orEmpty(), busy = current.busy,
            preparing = current.validating || current.loaded == null, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
    }

    fun choose(cursor: TimingCursor) {
        syncDraft()
        if (state.busy || cursor !in LyricTiming.cursors(state.lines)) return
        pressing.clear()
        state = state.copy(progress = TimingProgress(cursor), skipped = state.skipped - cursor.lineId, error = false)
        editor.selectLine(cursor.lineId)
    }

    fun move(delta: Int, seek: Boolean = false) = perform {
        syncDraft()
        val order = LyricTiming.cursors(state.lines, state.skipped)
        if (order.isEmpty()) return@perform
        val index = order.indexOf(state.cursor).takeIf { it >= 0 } ?: 0
        val target = order[(index + delta).coerceIn(0, order.lastIndex)]
        choose(target)
        if (seek) seekToCursor()
    }

    fun moveLine(delta: Int) {
        val rows = state.lines.filter { it.id !in state.skipped && it.words.any { word -> word.text.isNotBlank() } }
        if (rows.isEmpty()) return
        val index = rows.indexOfFirst { it.id == state.cursor?.lineId }.coerceAtLeast(0)
        val line = rows[(index + delta).coerceIn(0, rows.lastIndex)]
        choose(TimingCursor(line.id, line.words.indexOfFirst { it.text.isNotBlank() }))
    }

    fun boundary(last: Boolean) = perform {
        val words = LyricTiming.cursors(state.lines, state.skipped).filter { it.lineId == state.cursor?.lineId }
        (if (last) words.lastOrNull() else words.firstOrNull())?.let { choose(it); seekToCursor() }
    }

    fun press(mark: TimingMark) = perform {
        syncDraft()
        if (!state.canRecord || pressing.containsKey(mark) || state.judge != TimingJudge.DOWN && pressing.isNotEmpty()) return@perform
        val sample = read()
        pressing[mark] = Press(sample, state.cursor, System.nanoTime())
        if (state.judge == TimingJudge.DOWN) record(mark, sample.positionMs)
    }

    fun release(mark: TimingMark) = perform {
        val start = pressing.remove(mark) ?: return@perform
        if (state.judge == TimingJudge.DOWN || start.cursor != state.cursor) return@perform
        val end = read()
        val elapsedMs = (System.nanoTime() - start.at) / 1_000_000
        check(start.snapshot.trackId == end.trackId && start.snapshot.seekGeneration == end.seekGeneration && end.positionMs >= start.snapshot.positionMs &&
            end.positionMs - start.snapshot.positionMs <= elapsedMs * 2 + 500) { "按键期间发生切歌或跳转，本次按键未写入，请重试。" }
        val time = if (state.judge == TimingJudge.UP) end.positionMs else start.snapshot.positionMs + (end.positionMs - start.snapshot.positionMs) / 2
        record(mark, time)
    }

    fun click(mark: TimingMark) {
        if (!pressing.containsKey(mark)) press(mark)
        release(mark)
    }
    fun cancelPress(mark: TimingMark? = null) { if (mark == null) pressing.clear() else pressing.remove(mark) }

    private fun record(mark: TimingMark, time: Long) {
        val beforeProgress = state.progress
        val change = LyricTiming.mark(state.lines, beforeProgress, mark, time, state.options, state.skipped,
            state.emptyBeats[state.cursor] ?: 0)
        val before = change.lines.keys.associateWith { id -> state.lines.first { it.id == id } }
        check(apply(change.lines)) { "歌词草稿已变化，请重新选择后打轴。" }
        undo.addLast(Edit(before, change.lines, beforeProgress, change.progress))
        while (undo.size > 100) undo.removeFirst()
        redo.clear()
        state = state.copy(progress = change.progress, message = if (change.progress.cursor == null) "已到最后一个词；核对后写入歌曲文件。"
            else if (change.lines.isEmpty()) "空拍 ${change.progress.emptyBeat} / ${state.emptyBeats[state.cursor] ?: 0}"
            else "已记录 ${LyricEditTime.format((time + state.options.offsetMs).coerceAtLeast(0))}。", error = false)
        change.progress.cursor?.let { editor.selectLine(it.lineId) }
        syncDraft()
    }

    private fun apply(lines: Map<Int, EditableLyricLine>): Boolean {
        val expected = raw ?: return false
        if (lines.isNotEmpty() && !editor.applyTimingLines(expected, lines)) return false
        observedEdits = editor.state.lineEdits
        return true
    }

    fun undo(redoing: Boolean = false) = perform {
        syncDraft()
        val source = if (redoing) redo else undo
        val target = if (redoing) undo else redo
        val edit = source.peekLast() ?: return@perform
        check(apply(if (redoing) edit.after else edit.before)) { "草稿已变化，无法恢复这一步。" }
        source.removeLast(); target.addLast(edit); pressing.clear()
        state = state.copy(progress = if (redoing) edit.next else edit.previous, message = if (redoing) "已重做上一步打轴。" else "已撤销上一步打轴。", error = false)
        syncDraft()
    }

    fun prepare(mode: TimingSplit) { pressing.clear(); editor.prepareTimingWords(mode) }
    fun judge(value: TimingJudge) { pressing.clear(); state = state.copy(judge = value) }
    fun options(value: TimingOptions) {
        require(value.offsetMs in -2000..2000)
        pressing.clear(); state = state.copy(options = value)
    }
    fun emptyBeats(value: Int) {
        val cursor = state.cursor ?: return
        state = state.copy(emptyBeats = state.emptyBeats + (cursor to value.coerceIn(0, 16)), progress = state.progress.copy(emptyBeat = 0))
    }
    fun skipLine() {
        val id = state.cursor?.lineId ?: return
        val skipped = state.skipped + id
        val remaining = LyricTiming.cursors(state.lines, skipped)
        val next = remaining.firstOrNull { it.lineId > id } ?: remaining.firstOrNull()
        pressing.clear(); state = state.copy(skipped = skipped, progress = TimingProgress(next), message = "已跳过本句；点击本句中的词可恢复参与打轴。")
    }

    fun playPause() = perform { val sample = read(); val path = editor.state.loaded!!.path; if (sample.playing) transport.pause(path) else transport.play(path); cancelPress() }
    fun seek(deltaMs: Long) = perform { val sample = read(); transport.seek(editor.state.loaded!!.path, (sample.positionMs + deltaMs).coerceAtLeast(0)); cancelPress() }
    fun seekToCursor() = perform {
        val cursor = state.cursor ?: return@perform
        val word = state.lines.first { it.id == cursor.lineId }.words[cursor.word]
        transport.seek(editor.state.loaded!!.path, LyricEditTime.parse(word.start) ?: 0); cancelPress()
    }
    fun rate(value: Float) = perform { read(); transport.rate(editor.state.loaded!!.path, value.coerceIn(.5f, 2f)); cancelPress() }
    fun stepRate(delta: Float) = perform { rate((read().rate + delta).coerceIn(.5f, 2f)) }
    private fun read(): TimingPlaybackSnapshot = transport.read(editor.state.loaded?.path ?: error("请先读取歌曲文件。")).also { playback = it }
    private fun perform(action: () -> Unit) { try { action() } catch (error: Exception) { pressing.clear(); fail(error) } }
    private fun fail(error: Exception) {
        val cause = if (error is InvocationTargetException) error.targetException else error
        state = state.copy(error = true, message = cause.message ?: "宿主操作未完成，请检查当前播放歌曲。")
    }
}
