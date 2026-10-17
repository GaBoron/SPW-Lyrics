package dev.gaboron.spwlyrics.integration.batchui

import dev.gaboron.spwlyrics.application.LyricsBatchProcessor
import dev.gaboron.spwlyrics.integration.composeui.DesktopToolWindow
import dev.gaboron.spwlyrics.integration.composeui.DesktopWindowContent

internal class BatchProcessingWindow(
    processor: LyricsBatchProcessor,
    onFailure: (String) -> Unit,
    describeFailure: (Throwable) -> String = { "操作失败：${it.message ?: "未知错误"}" },
) : AutoCloseable {
    private val window = DesktopToolWindow("SPW Lyrics · 批量处理音乐库", "batch-window-position.json", onFailure) {
        val controller = BatchProcessingController(processor, describeFailure)
        controller.load()
        DesktopWindowContent({ BatchProcessingContent(controller) }, controller::close)
    }

    fun open() = window.open()
    override fun close() = window.close()
}
