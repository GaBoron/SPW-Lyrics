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
import dev.gaboron.spwlyrics.integration.workspace.LyricsTransition
import dev.gaboron.spwlyrics.integration.workspace.lyricVoiceLabel
import dev.gaboron.spwlyrics.domain.LyricVocalPart

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
            val listWidth = (maxWidth * .33f).coerceIn(240.dp, 320.dp)
            if (maxWidth < 760.dp) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricsMenu(selected?.let { "第 ${state.lineNumbers[it.id]} 句 · ${lyricVoiceLabel(state.vocalPart(it) == LyricVocalPart.DUET, it.background)}  ${state.lineText(it.id).take(12)}" } ?: "选择歌词句子",
                    rows.map { it.id }, state.selectedLineId ?: rows.first().id, { id ->
                        val line = state.line(id)!!
                        "第 ${state.lineNumbers[id]} 句 · ${lyricVoiceLabel(state.vocalPart(line) == LyricVocalPart.DUET, line.background)}  ${state.lineText(id).take(24)}"
                    },
                    { controller.selectLine(it, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed) })
                LyricsTransition(selected?.id, Modifier.weight(1f).fillMaxWidth()) { id ->
                    id?.let(state::line)?.let { MetadataLyricLineEditor(it, controller, Modifier.fillMaxSize()) }
                }
            } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataLineList(controller, Modifier.width(listWidth).fillMaxHeight())
                LyricsTransition(selected?.id, Modifier.weight(1f).fillMaxHeight()) { id ->
                    id?.let(state::line)?.let { MetadataLyricLineEditor(it, controller, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
