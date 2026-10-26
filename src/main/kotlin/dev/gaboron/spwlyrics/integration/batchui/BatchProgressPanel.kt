package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsSection
import dev.gaboron.spwlyrics.integration.workspace.LyricsTransition

@Composable
internal fun BatchProgressPanel(state: BatchProcessingState, summary: BatchProgressSummary, modifier: Modifier) {
    val progress by animateFloatAsState(summary.progress.coerceIn(0f, 1f), tween(300), label = "batch progress")
    LyricsSection(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LyricsTransition(state.stateText, Modifier.weight(1f)) { Text(it) }
                Text("${summary.processed} / ${summary.selected} 首", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(SaltTheme.colors.stroke)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(SaltTheme.colors.highlight))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${if (state.snapshot.writingMetadata) "已处理" else "新缓存"} ${summary.completed}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                Text("已有缓存 ${summary.cached}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                Text("失败 ${summary.failed}", color = if (summary.failed > 0) SaltTheme.colors.error else SaltTheme.colors.subText,
                    style = SaltTheme.textStyles.sub)
                Text(if (state.snapshot.writingMetadata) "最多同时处理 3 首 · 写入已选择的歌曲数据" else "最多同时处理 3 首 · 只写入插件缓存",
                    color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            }
        }
    }
}
