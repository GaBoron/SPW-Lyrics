package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.EmptyWorkspace
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu

@Composable
internal fun MetadataStructuredEditor(controller: MetadataEditorController) {
    val state = controller.state
    val rows = state.structured?.lines.orEmpty()
    val window = LocalWindowInfo.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetadataLyricActions(controller)
        if (rows.isEmpty()) {
            EmptyWorkspace(if (state.validating) "正在读取歌词" else "暂无可编辑歌词",
                state.structuredError ?: "添加一句歌词，或从文件、搜索结果导入。", loading = state.validating)
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val selected = state.selectedLineId?.let(state::line)
            if (maxWidth < 640.dp) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricsMenu(selected?.let { "第 ${it.id + 1} 句  ${state.lineText(it.id).take(16)}" } ?: "选择歌词句子",
                    rows.map { it.id }, state.selectedLineId ?: rows.first().id, { "第 ${it + 1} 句  ${state.lineText(it).take(24)}" },
                    { controller.selectLine(it, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed) })
                if (selected != null) MetadataLyricLineEditor(selected, controller, Modifier.weight(1f).fillMaxWidth())
            } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataLineList(controller, Modifier.width(180.dp).fillMaxHeight())
                if (selected != null) MetadataLyricLineEditor(selected, controller, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
