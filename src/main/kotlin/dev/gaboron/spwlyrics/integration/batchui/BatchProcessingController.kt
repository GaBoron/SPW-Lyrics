package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchProcessor
import dev.gaboron.spwlyrics.application.LyricsBatchSnapshot
import dev.gaboron.spwlyrics.application.LyricsBatchState
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Serializes commands and snapshot reads, preserving selection without owning batch work. */
internal class BatchProcessingController(
    private val processor: LyricsBatchProcessor,
    private val describeFailure: (Throwable) -> String,
) : AutoCloseable {
    var state by mutableStateOf(BatchProcessingState())
        private set
    @Volatile private var closed = false
    private var polling = false
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "spw-lyrics-batch-window").apply { isDaemon = true }
    }

    init {
        worker.scheduleWithFixedDelay({
            EventQueue.invokeLater {
                if (!closed && state.active && !state.busy && !polling) request(poll = true) { processor.snapshot() }
            }
        }, 650, 650, TimeUnit.MILLISECONDS)
    }

    fun load() = request { processor.snapshot(loadLibrary = true) }
    fun refresh() { if (state.editable) request { processor.reloadLibrary() } }
    fun includeCached(value: Boolean) { if (state.editable) state = state.copy(includeCached = value) }
    fun select(key: String) {
        if (state.editable) state = state.copy(selectedKeys = if (key in state.selectedKeys) state.selectedKeys - key else state.selectedKeys + key)
    }
    fun selectAll(value: Boolean) {
        if (state.editable) state = state.copy(selectedKeys = if (value) state.keys.toSet() else emptySet())
    }

    fun start() {
        if (!state.editable || state.selectedKeys.isEmpty()) return
        val selected = state.selectedKeys
        val includeCached = state.includeCached
        request(preserveSelection = false) { processor.start(includeCached, selected) }
    }

    fun pauseOrResume() {
        if (state.busy || !state.active) return
        val resume = state.snapshot.state == LyricsBatchState.PAUSED
        request(preserveSelection = false) { if (resume) processor.resume() else processor.pause() }
    }

    fun stop() { if (!state.busy && state.active) request(preserveSelection = false) { processor.cancel() } }
    fun retry() {
        if (state.editable && state.snapshot.items.any { it.state == LyricsBatchItemState.FAILED }) {
            request(preserveSelection = false) { processor.start(includeCached = false, retryFailed = true) }
        }
    }

    private fun request(preserveSelection: Boolean = true, poll: Boolean = false, action: () -> LyricsBatchSnapshot) {
        if (closed || !poll && state.busy && state.keys.isNotEmpty()) return
        if (poll) polling = true else state = state.copy(busy = true, error = false)
        runCatching {
            worker.execute {
                val result = runCatching(action)
                EventQueue.invokeLater {
                    if (closed) return@invokeLater
                    polling = false
                    result.onSuccess { display(it, preserveSelection) }.onFailure { failure(it) }
                    if (!poll) state = state.copy(busy = false)
                }
            }
        }.onFailure { polling = false; if (!closed) { failure(it); state = state.copy(busy = false) } }
    }

    private fun display(snapshot: LyricsBatchSnapshot, preserveSelection: Boolean) {
        val sameTracks = state.snapshot.items.map { it.query } == snapshot.items.map { it.query }
        val keys = if (sameTracks) state.keys else snapshot.items.map { it.query.key }
        val oldKeys = state.keys.toSet()
        val selected = if (preserveSelection && sameTracks) state.selectedKeys else keys.filterIndexed { index, key ->
            if (preserveSelection && key in oldKeys) key in state.selectedKeys else snapshot.items[index].selected
        }.toSet()
        state = state.copy(snapshot = snapshot, keys = keys, selectedKeys = selected,
            message = if (snapshot.items.isEmpty()) "没有可处理的歌曲，请先完成 SPW 音乐库扫描，再重新读取音乐库。"
            else "关闭窗口后任务继续；停止任务会保留已写入的缓存。", error = false)
    }

    private fun failure(error: Throwable) { state = state.copy(error = true, message = describeFailure(error)) }
    // Finish already accepted commands; disposing a view must not stop the processor.
    override fun close() { closed = true; worker.shutdown() }
}
