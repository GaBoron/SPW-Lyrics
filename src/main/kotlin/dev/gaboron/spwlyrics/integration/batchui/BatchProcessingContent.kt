package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchState
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun BatchProcessingContent(controller: BatchProcessingController) {
    val state = controller.state
    val summary = remember(state.snapshot, state.selectedKeys) { BatchProgressSummary.from(state) }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.refresh(); true } else false
    }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LyricsStatus("为音乐库提前搜索并缓存歌词，离开页面后任务仍会继续。")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton(if (state.snapshot.state in listOf(LyricsBatchState.COMPLETED, LyricsBatchState.CANCELLED)) "重新处理" else "开始处理",
                controller::start, enabled = state.editable && state.selectedKeys.isNotEmpty(), primary = !state.active, loading = state.busy)
            if (state.active) {
                DesktopButton(if (state.snapshot.state == LyricsBatchState.PAUSED) "继续处理" else "暂停", controller::pauseOrResume,
                    enabled = !state.busy, primary = true)
                DesktopButton("停止", controller::stop, enabled = !state.busy)
            }
            DesktopButton("重试失败项", controller::retry,
                enabled = state.editable && state.snapshot.items.any { it.state == LyricsBatchItemState.FAILED })
            DesktopButton("刷新音乐库", controller::refresh, enabled = state.editable, glyph = DesktopGlyph.Refresh)
        }
        BatchProgressPanel(state, summary, Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("已选 ${summary.selected} / ${state.snapshot.items.size} 首", Modifier.padding(vertical = 8.dp), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            DesktopCheck(state.includeCached, { controller.includeCached(!state.includeCached) }, state.editable, "重新处理已有缓存")
            DesktopButton("全选", { controller.selectAll(true) }, enabled = state.editable && state.keys.isNotEmpty())
            DesktopButton("取消全选", { controller.selectAll(false) }, enabled = state.editable && state.keys.isNotEmpty())
        }
        LyricsStatus(state.message, state.error)
        BatchTrackList(state, controller::select, Modifier.weight(1f).fillMaxWidth())
    }
}
