package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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

private val lineListPadding = 4.dp
private val lineDragHandleWidth = 28.dp

@Composable
internal fun MetadataLineList(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    val scroll = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    val reorder = rememberMetadataLineReorder(controller, scroll)
    val rows = reorder.rows()
    val shape = SaltTheme.shapes.medium
    Box(modifier.focusRequester(focus).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.A) { controller.selectAllLines(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.Delete) { controller.deleteSelected(); true }
        else false
    }.lineDragInput(reorder, scroll, state.canEditRows && !state.validating) { id ->
        focus.requestFocus()
        controller.selectLine(id)
    }.focusable()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll, contentPadding = PaddingValues(lineListPadding)) {
            items(rows, key = { it.id }) { original ->
                val line = state.line(original.id) ?: original
                val moving = reorder.draggingId == line.id || reorder.settlingId == line.id
                Column(Modifier.fillMaxWidth()
                    .animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (moving) null else spring(stiffness = 650f, dampingRatio = 1f))
                    .zIndex(if (moving) 1f else 0f)
                    .graphicsLayer { translationY = reorder.offset(line.id) }) {
                    Row(Modifier.fillMaxWidth()
                        .background(if (moving) SaltTheme.colors.subBackground else Color.Transparent, shape)
                        .lyricsSelection(line.id in state.selectedLineIds)
                        .border(1.dp, if (moving) SaltTheme.colors.highlight.copy(alpha = .3f) else Color.Transparent, shape)
                        .clickable(enabled = !state.busy && !reorder.active) {
                            focus.requestFocus()
                            controller.selectLine(line.id, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(lineDragHandleWidth).height(48.dp)
                            .semantics { contentDescription = "拖动调整歌词顺序" }, contentAlignment = Alignment.Center) {
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

/** The list owns the captured gesture so recycling a moving row cannot cancel it at a viewport edge. */
private fun Modifier.lineDragInput(reorder: MetadataLineReorder, list: LazyListState, enabled: Boolean,
    select: (Int) -> Unit): Modifier = pointerInput(reorder, list, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val leading = lineListPadding.toPx()
        if (down.position.x !in leading..(leading + lineDragHandleWidth.toPx())) return@awaitEachGesture
        val id = list.layoutInfo.visibleItemsInfo.firstOrNull {
            down.position.y >= it.offset && down.position.y < it.offset + it.size
        }?.key as? Int ?: return@awaitEachGesture
        var overSlop = 0f
        val start = awaitTouchSlopOrCancellation(down.id) { change, offset ->
            overSlop = offset.y
            change.consume()
        } ?: return@awaitEachGesture
        select(id)
        reorder.start(id)
        if (reorder.draggingId != id) return@awaitEachGesture
        try {
            reorder.move(overSlop)
            val released = drag(start.id) { change ->
                val delta = change.positionChange().y
                change.consume()
                reorder.move(delta)
            }
            reorder.finish(cancelled = !released)
        } finally {
            if (reorder.draggingId == id) reorder.finish(cancelled = true)
        }
    }
}
