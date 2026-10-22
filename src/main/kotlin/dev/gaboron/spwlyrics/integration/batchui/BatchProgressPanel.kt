package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsSection

@Composable
internal fun BatchProgressPanel(state: BatchProcessingState, summary: BatchProgressSummary, modifier: Modifier) {
    LyricsSection(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.stateText, Modifier.weight(1f))
                Text("${summary.processed} / ${summary.selected} 首", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(SaltTheme.colors.stroke)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(summary.progress.coerceIn(0f, 1f)).background(SaltTheme.colors.highlight))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("新缓存 ${summary.completed}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                Text("已有缓存 ${summary.cached}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                Text("失败 ${summary.failed}", color = if (summary.failed > 0) SaltTheme.colors.error else SaltTheme.colors.subText,
                    style = SaltTheme.textStyles.sub)
                Text("最多同时处理 3 首 · 只写入插件缓存", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            }
        }
    }
}
