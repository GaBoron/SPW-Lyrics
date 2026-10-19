package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.domain.LyricVocalPart

@Composable
internal fun MetadataStructuredEditor(controller: MetadataEditorController) {
    val state = controller.state
    val rows = state.structured?.lines.orEmpty()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetadataLyricActions(controller)
        if (rows.isEmpty()) {
            EmptyWorkspace(if (state.validating) "正在读取歌词" else "暂无可编辑歌词",
                state.structuredError ?: "点击“添加歌词”新建，或导入、搜索后逐句编辑。", loading = state.validating)
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val listWidth = (maxWidth * .27f).coerceIn(145.dp, 200.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val scroll = rememberLazyListState()
                val focus = remember { FocusRequester() }
                val window = LocalWindowInfo.current
                Box(Modifier.width(listWidth).fillMaxHeight().desktopPanel().focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.A) { controller.selectAllLines(); true }
                    else if (it.type == KeyEventType.KeyDown && it.key == Key.Delete) { controller.deleteSelected(); true }
                    else false
                }.focusable()) {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = scroll) {
                        items(rows, key = { it.id }) { original ->
                            val line = state.line(original.id) ?: original
                            Column(Modifier.fillMaxWidth()
                                .background(if (line.id in state.selectedLineIds) DesktopColors.Selected else DesktopColors.Surface)
                                .clickable(enabled = !state.busy) {
                                    focus.requestFocus()
                                    controller.selectLine(line.id, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed)
                                }.padding(10.dp, 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${line.id + 1}  ${line.start.ifBlank { "未定时" }}", color = DesktopColors.Muted, fontSize = 10.sp)
                                ElidedText(state.lineText(line.id), size = 12)
                            Text(
                                listOfNotNull(if (state.vocalPart(line) == LyricVocalPart.MAIN) "主唱 · 左" else "对唱 · 右", "背景".takeIf { line.background }).joinToString(" / "),
                                    fontSize = 10.sp, color = DesktopColors.Accent)
                            }
                            DesktopDivider()
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
                state.selectedLineId?.let(state::line)?.let { line ->
                    MetadataLyricLineEditor(line, controller, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
