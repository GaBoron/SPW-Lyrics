package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun BatchProgressPanel(state: BatchProcessingState, summary: BatchProgressSummary, modifier: Modifier) {
    Column(modifier.desktopPanel().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("任务概览", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(state.stateText, color = DesktopColors.Accent, fontSize = 12.sp)
        Text("${(summary.progress * 100).toInt()}%", fontSize = 32.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.fillMaxWidth().height(5.dp).background(DesktopColors.Border)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(summary.progress.coerceIn(0f, 1f)).background(DesktopColors.Accent))
        }
        Text("已处理 ${summary.processed} / ${summary.selected} 首", color = DesktopColors.Muted, fontSize = 12.sp)
        DesktopDivider()
        listOf("新缓存" to summary.completed, "已有缓存" to summary.cached, "需要重试" to summary.failed).forEach { (label, count) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DesktopColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(count.toString(), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = if (label == "需要重试" && count > 0) DesktopColors.Error else DesktopColors.Text)
            }
        }
        DesktopDivider()
        Text("最多同时处理 3 首歌曲", fontSize = 11.sp, color = DesktopColors.Muted)
        Text("关闭窗口后任务继续运行。\n重新打开即可查看进度。", fontSize = 11.sp, lineHeight = 19.sp, color = DesktopColors.Muted)
        Text("只写入插件缓存，不修改音频文件。", fontSize = 11.sp, lineHeight = 19.sp, color = DesktopColors.Muted)
    }
}
