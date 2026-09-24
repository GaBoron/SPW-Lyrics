package dev.gaboron.spwlyrics.integration.manualui

import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchProcessor
import dev.gaboron.spwlyrics.application.LyricsBatchSnapshot
import dev.gaboron.spwlyrics.application.LyricsBatchState

internal class BatchUiSession(private val processor: LyricsBatchProcessor) {
    fun handle(request: ManualUiRequest): ManualUiResponse = try {
        when (request.action) {
            "batch_state" -> response(processor.snapshot(loadLibrary = true))
            "batch_start" -> response(processor.start(request.includeCached, request.selectedKeys?.toSet()))
            "batch_pause" -> response(processor.pause())
            "batch_resume" -> response(processor.resume())
            "batch_cancel" -> response(processor.cancel())
            "batch_retry" -> response(processor.start(includeCached = false, retryFailed = true))
            else -> ManualUiResponse(false, "未知批处理请求。")
        }
    } catch (error: PluginPermissionDeniedException) {
        ManualUiResponse(false, "请在 SPW 的插件权限中授予曲库读取权限后重试。")
    } catch (error: Exception) {
        ManualUiResponse(false, "批量处理失败：${error.message ?: "未知错误"}")
    }

    private fun response(snapshot: LyricsBatchSnapshot): ManualUiResponse {
        val selectedItems = snapshot.items.filter { it.selected }
        val completed = snapshot.items.count { it.state == LyricsBatchItemState.COMPLETED }
        val failed = snapshot.items.count { it.state == LyricsBatchItemState.FAILED }
        val cached = snapshot.items.count { it.state == LyricsBatchItemState.CACHED }
        val processed = snapshot.items.count {
            it.state == LyricsBatchItemState.COMPLETED || it.state == LyricsBatchItemState.FAILED ||
                it.state == LyricsBatchItemState.CACHED
        }
        return ManualUiResponse(
            ok = true,
            batch = BatchUiSnapshot(
                state = snapshot.state.name.lowercase(),
                stateLabel = stateLabel(snapshot),
                total = snapshot.items.size,
                selected = selectedItems.size,
                progress = selectedItems.map { it.progress }.average().takeUnless { it.isNaN() } ?: 0.0,
                processed = processed,
                completed = completed,
                failed = failed,
                cached = cached,
                items = snapshot.items.map { item ->
                    BatchUiItem(
                        key = item.query.key,
                        title = item.query.title,
                        artists = item.query.artists.joinToString(" / "),
                        album = item.query.album,
                        state = item.state.name.lowercase(),
                        stateLabel = itemStateLabel(item.state),
                        selected = item.selected,
                        progress = item.progress,
                        stage = item.stage,
                        source = item.source,
                        quality = item.quality,
                        message = item.message,
                    )
                },
            ),
        )
    }

    private fun stateLabel(snapshot: LyricsBatchSnapshot): String = when (snapshot.state) {
        LyricsBatchState.IDLE -> "准备就绪"
        LyricsBatchState.RUNNING -> runningLabel(snapshot)
        LyricsBatchState.PAUSED -> "已暂停，新歌曲暂不开始"
        LyricsBatchState.COMPLETED -> "处理完成"
        LyricsBatchState.CANCELLED -> "已停止"
    }

    private fun runningLabel(snapshot: LyricsBatchSnapshot): String {
        val stages = snapshot.items.filter { it.state == LyricsBatchItemState.SEARCHING }
            .groupingBy { it.stage }.eachCount()
            .entries.joinToString(" · ") { (stage, count) -> "$stage $count" }
        return if (stages.isBlank()) "正在处理" else "正在处理 · $stages"
    }

    private fun itemStateLabel(state: LyricsBatchItemState): String = when (state) {
        LyricsBatchItemState.WAITING -> "等待处理"
        LyricsBatchItemState.SEARCHING -> "正在搜索"
        LyricsBatchItemState.COMPLETED -> "已完成"
        LyricsBatchItemState.FAILED -> "需要重试"
        LyricsBatchItemState.CACHED -> "已有缓存"
        LyricsBatchItemState.EXCLUDED -> "未选择"
        LyricsBatchItemState.CANCELLED -> "已停止"
    }
}
