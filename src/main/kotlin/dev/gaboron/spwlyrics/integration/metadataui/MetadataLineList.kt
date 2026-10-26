package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon

@Composable
internal fun MetadataLineList(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    val scroll = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    val reorder = rememberMetadataLineReorder(controller, scroll)
    val rows = reorder.rows()
    Box(modifier.focusRequester(focus).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.A) { controller.selectAllLines(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.Delete) { controller.deleteSelected(); true }
        else false
    }.focusable()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll, contentPadding = PaddingValues(4.dp)) {
            items(rows, key = { it.id }) { original ->
                val line = state.line(original.id) ?: original
                val moving = reorder.draggingId == line.id || reorder.settlingId == line.id
                Column(Modifier.fillMaxWidth()
                    .animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (moving) null else spring(stiffness = 650f, dampingRatio = 1f))
                    .zIndex(if (moving) 1f else 0f)
                    .graphicsLayer { translationY = reorder.offset(line.id); shadowElevation = if (moving) 8.dp.toPx() else 0f }) {
                    Row(Modifier.fillMaxWidth()
                        .background(if (moving) SaltTheme.colors.subBackground else androidx.compose.ui.graphics.Color.Transparent, SaltTheme.shapes.medium)
                        .lyricsSelection(line.id in state.selectedLineIds)
                        .clickable(enabled = !state.busy && !reorder.active) {
                            focus.requestFocus()
                            controller.selectLine(line.id, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(28.dp).height(48.dp).semantics { contentDescription = "拖动调整歌词顺序" }
                            .pointerInput(line.id, state.canEditRows, state.validating) {
                                if (state.canEditRows && !state.validating) detectDragGestures(
                                    onDragStart = {
                                        focus.requestFocus()
                                        controller.selectLine(line.id)
                                        reorder.start(line.id)
                                    },
                                    onDrag = { change, amount -> change.consume(); reorder.move(amount.y) },
                                    onDragCancel = { reorder.finish(cancelled = true) },
                                    onDragEnd = { reorder.finish() })
                            }, contentAlignment = Alignment.Center) {
                            DesktopIcon(DesktopGlyph.Drag, SaltTheme.colors.subText, Modifier.size(15.dp))
                        }
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${state.lineNumbers[line.id]}  ${line.start.ifBlank { "未定时" }}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            Text(state.lineText(line.id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(lyricVoiceLabel(state.vocalPart(line) == LyricVocalPart.DUET, line.background),
                                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                        }
                    }
                    LyricsListDivider()
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
