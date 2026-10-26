package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.EmptyWorkspace

@Composable
internal fun MetadataStructuredEditor(controller: MetadataEditorController) {
    val state = controller.state
    val rows = state.structured?.lines.orEmpty()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetadataLyricActions(controller)
        if (rows.isEmpty()) {
            EmptyWorkspace(if (state.validating) "正在读取歌词" else "暂无可编辑歌词",
                state.structuredError ?: "添加一句歌词，或从文件、搜索结果导入。", loading = state.validating)
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val listWidth = (maxWidth * .33f).coerceIn(240.dp, 320.dp)
            val properties: @Composable (Modifier) -> Unit = { modifier ->
                MetadataLyricLineEditor(controller, modifier)
            }
            if (maxWidth < 760.dp) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataLineList(controller, Modifier.fillMaxWidth().weight(.4f))
                properties(Modifier.weight(.6f).fillMaxWidth())
            } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataLineList(controller, Modifier.width(listWidth).fillMaxHeight())
                properties(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
