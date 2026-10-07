package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchState
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun BatchProcessingContent(controller: BatchProcessingController) {
    val state = controller.state
    val summary = remember(state.snapshot, state.selectedKeys) { BatchProgressSummary.from(state) }
    Column(Modifier.fillMaxSize().background(DesktopColors.Background).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.refresh(); true } else false
    }) {
        Column(Modifier.weight(1f).padding(24.dp, 20.dp, 24.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("批量处理音乐库", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("为 SPW 音乐库提前搜索并缓存歌词", fontSize = 14.sp, color = DesktopColors.Muted)
                }
                DesktopButton("重新读取音乐库", controller::refresh, enabled = state.editable,
                    loading = state.busy && state.snapshot.items.isEmpty(), glyph = DesktopGlyph.Refresh)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopButton("全选", { controller.selectAll(true) }, enabled = state.editable && state.keys.isNotEmpty())
                DesktopButton("取消全选", { controller.selectAll(false) }, enabled = state.editable && state.keys.isNotEmpty())
                Text("已选择 ${summary.selected} / ${state.snapshot.items.size}", Modifier.padding(start = 5.dp), fontSize = 12.sp, color = DesktopColors.Muted)
                Spacer(Modifier.weight(1f))
                DesktopCheck(state.includeCached, { controller.includeCached(!state.includeCached) }, state.editable, "重新处理已有缓存")
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BatchTrackList(state, controller::select, Modifier.weight(1f).fillMaxHeight())
                BatchProgressPanel(state, summary, Modifier.width(270.dp).fillMaxHeight())
            }
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().background(DesktopColors.Subtle).padding(24.dp, 13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElidedText(state.message, Modifier.weight(1f).padding(end = 12.dp), color = if (state.error) DesktopColors.Error else DesktopColors.Muted, size = 11)
            DesktopButton("重试失败项", controller::retry,
                enabled = state.editable && state.snapshot.items.any { it.state == LyricsBatchItemState.FAILED })
            DesktopButton("停止", controller::stop, enabled = !state.busy && state.active)
            DesktopButton(if (state.snapshot.state == LyricsBatchState.PAUSED) "继续" else "暂停", controller::pauseOrResume,
                enabled = !state.busy && state.active, primary = state.active)
            DesktopButton(if (state.snapshot.state == LyricsBatchState.COMPLETED || state.snapshot.state == LyricsBatchState.CANCELLED) "重新处理" else "开始处理",
                controller::start, enabled = state.editable && state.selectedKeys.isNotEmpty(), primary = !state.active, loading = state.busy)
        }
    }
}
