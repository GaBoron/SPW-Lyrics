package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.EmptyWorkspace
import dev.gaboron.spwlyrics.integration.workspace.LyricsTransition
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

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
            val selected = state.selectedLineId?.let(state::line)
            val listWidth = (maxWidth * .33f).coerceIn(240.dp, 320.dp)
            val single = state.selectedLineIds.size == 1
            val properties: @Composable (Modifier) -> Unit = { modifier ->
                LyricsTransition(if (single) selected?.id else null, modifier) { id ->
                    if (id != null) id.let(state::line)?.let { MetadataLyricLineEditor(it, controller, Modifier.fillMaxSize()) }
                    else Column(Modifier.padding(12.dp)) {
                        LyricsStatus(if (state.selectedLineIds.isEmpty()) "选择一句歌词后编辑内容。"
                            else "已选 ${state.selectedLineIds.size} 句，可使用上方批量操作。选择单句后可编辑正文、时间、翻译和音译。")
                    }
                }
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
