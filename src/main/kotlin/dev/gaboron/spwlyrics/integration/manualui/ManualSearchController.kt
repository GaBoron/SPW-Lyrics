package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.storage.LyricsDelaySettings
import java.awt.EventQueue
import java.util.concurrent.Executors

/** Owns asynchronous requests and presentation state; domain rules stay behind operations. */
internal class ManualSearchController(private val operations: ManualSearchOperations) : AutoCloseable {
    var state by mutableStateOf(ManualSearchState())
        private set
    private val workers = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "spw-lyrics-manual-search").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    private var trackGeneration = 0
    private var searchGeneration = 0
    private var previewGeneration = 0

    fun refreshTrack() {
        if (closed || state.mutating || state.readingTrack && trackGeneration > 0) return
        val generation = ++trackGeneration
        searchGeneration++
        previewGeneration++
        state = ManualSearchState(source = state.source)
        submit({ trackGeneration == generation }, {
            operations.currentQuery()?.let { it to operations.delay(it) }
        }, { loaded ->
            state = state.copy(
                query = loaded?.first, keywords = loaded?.first?.title.orEmpty(),
                delayText = (loaded?.second ?: 0).toString(), savedDelay = loaded?.second ?: 0,
                readingTrack = false, status = if (loaded == null) "请先播放歌曲，再点击刷新当前歌曲。" else "正在搜索…",
            )
            if (loaded != null) search()
        }, { state = state.copy(readingTrack = false); reportFailure(it) })
    }

    fun keywords(value: String) { state = state.copy(keywords = value) }
    fun source(value: LyricsSource?) { state = state.copy(source = value) }
    fun delayText(value: String) {
        if (value.length <= 7 && value.matches(Regex("-?\\d*"))) state = state.copy(delayText = value)
    }

    fun stepDelay(delta: Int) {
        val value = state.delayValue ?: return invalidDelay()
        state = state.copy(delayText = (value + delta).coerceIn(LyricsDelaySettings.MIN_MS, LyricsDelaySettings.MAX_MS).toString())
    }

    fun search() {
        if (!state.canSearch) return
        val query = state.query ?: return
        val keywords = state.keywords.trim()
        if (keywords.isEmpty()) { status("请输入搜索关键词。", true); return }
        val source = state.source
        val generation = ++searchGeneration
        previewGeneration++
        state = state.copy(candidates = emptyList(), selected = null, document = null,
            searching = true, previewing = false, searched = false, status = "正在搜索…", error = false)
        submit({ searchGeneration == generation }, { operations.search(query, keywords, source) }, { rows ->
            state = state.copy(candidates = rows, searching = false, searched = true,
                status = if (rows.isEmpty()) "没有找到候选，请缩短关键词或换一个来源。" else "找到 ${rows.size} 个候选，请核对录音版本后应用。")
        }, { state = state.copy(searching = false, searched = true); reportFailure(it) })
    }

    fun select(row: CandidateScore) {
        if (closed || state.mutating || row !in state.candidates || state.selected == row) return
        val generation = ++previewGeneration
        state = state.copy(selected = row, document = null, previewing = true)
        submit({ previewGeneration == generation }, { operations.preview(row.candidate) }, { resolved ->
            state = state.copy(document = resolved?.document, previewing = false)
        }, { state = state.copy(previewing = false); reportFailure(it) })
    }

    fun apply() {
        val query = state.query ?: return
        val candidate = state.selected?.candidate ?: return
        mutate("歌词已应用并保存为当前歌曲的手动选择。", action = { operations.apply(query, candidate) })
    }

    fun useLocal() {
        val query = state.query ?: return
        mutate("已切回 SPW 本地歌词流程。", action = { operations.useLocal(query) })
    }

    fun useAutomatic() {
        val query = state.query ?: return
        mutate("已清除手动选择，正在重新自动匹配。", action = { operations.useAutomatic(query) })
    }

    fun saveDelay(reset: Boolean = false) {
        if (!state.canMutate) return
        val query = state.query ?: return
        val value = if (reset) 0 else state.delayValue ?: return invalidDelay()
        if (reset) state = state.copy(delayText = "0")
        mutate("已保存本曲延迟：$value ms；未刷新时可切歌后再切回。", {
            operations.setDelay(query, value)
        }) { state = state.copy(savedDelay = value) }
    }

    fun openBatch() = operations.openBatch()

    private fun mutate(message: String, action: () -> Boolean, success: () -> Unit = {}) {
        if (!state.canMutate) return
        val generation = trackGeneration
        state = state.copy(mutating = true, error = false, status = "正在处理…")
        submit({ trackGeneration == generation }, action, { result ->
            state = state.copy(mutating = false)
            if (result) { success(); status(message) }
            else status("操作未完成：当前歌曲可能已变化，请刷新当前歌曲后重试。", true)
        }, { state = state.copy(mutating = false); reportFailure(it) })
    }

    private fun status(message: String, error: Boolean = false) { state = state.copy(status = message, error = error) }
    private fun invalidDelay() = status("请输入 -60000 到 60000 之间的整数毫秒。", true)
    private fun reportFailure(error: Throwable) = status("操作失败：${error.message ?: "未知错误"}", true)

    private fun <T> submit(current: () -> Boolean, action: () -> T, complete: (T) -> Unit, failure: (Throwable) -> Unit) {
        if (closed) return
        runCatching {
            workers.execute {
                val result = runCatching(action)
                EventQueue.invokeLater {
                    if (!closed && current()) result.onSuccess(complete).onFailure(failure)
                }
            }
        }.onFailure { if (!closed && current()) failure(it) }
    }

    // A submitted apply/save may finish after the user closes the view, as before.
    override fun close() { closed = true; workers.shutdown() }
}
