package dev.gaboron.spwlyrics.integration.batchui

import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchSnapshot
import dev.gaboron.spwlyrics.application.LyricsBatchState

internal data class BatchProcessingState(
    val snapshot: LyricsBatchSnapshot = LyricsBatchSnapshot(LyricsBatchState.IDLE, emptyList()),
    val keys: List<String> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val includeCached: Boolean = false,
    val busy: Boolean = true,
    val error: Boolean = false,
    val message: String = "正在读取音乐库…",
) {
    val active: Boolean get() = snapshot.state == LyricsBatchState.RUNNING || snapshot.state == LyricsBatchState.PAUSED
    val editable: Boolean get() = !busy && !active
    val stateText: String get() = when (snapshot.state) {
        LyricsBatchState.IDLE -> "准备就绪"
        LyricsBatchState.RUNNING -> "正在处理"
        LyricsBatchState.PAUSED -> "已暂停，新歌曲暂不开始"
        LyricsBatchState.COMPLETED -> "处理完成"
        LyricsBatchState.CANCELLED -> "已停止"
    }
}

internal data class BatchProgressSummary(
    val selected: Int, val processed: Int, val completed: Int, val cached: Int, val failed: Int, val progress: Float,
) {
    companion object {
        fun from(state: BatchProcessingState): BatchProgressSummary {
            val items = state.snapshot.items.filterIndexed { index, _ -> state.keys[index] in state.selectedKeys }
            val completed = items.count { it.state == LyricsBatchItemState.COMPLETED }
            val cached = items.count { it.state == LyricsBatchItemState.CACHED }
            val failed = items.count { it.state == LyricsBatchItemState.FAILED }
            return BatchProgressSummary(items.size, completed + cached + failed, completed, cached, failed,
                items.map { it.progress }.average().takeUnless { it.isNaN() }?.toFloat() ?: 0f)
        }
    }
}
