package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun MetadataLineList(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    val scroll = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    val rows = state.structured?.lines.orEmpty()
    var dragging by remember { mutableStateOf<Int?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val drop = scroll.layoutInfo.visibleItemsInfo.firstOrNull {
        center >= it.offset && center <= it.offset + it.size
    }
    LaunchedEffect(dragging) {
        while (dragging != null && isActive) {
            val layout = scroll.layoutInfo
            val delta = when {
                center < layout.viewportStartOffset + 48 -> -12f
                center > layout.viewportEndOffset - 48 -> 12f
                else -> 0f
            }
            if (delta != 0f) scroll.scrollBy(delta)
            delay(16)
        }
    }
    Box(modifier.focusRequester(focus).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.A) { controller.selectAllLines(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.Delete) { controller.deleteSelected(); true }
        else false
    }.focusable()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll, contentPadding = PaddingValues(4.dp)) {
            items(rows, key = { it.id }) { original ->
                val line = state.line(original.id) ?: original
                val moving = dragging == line.id
                val position = scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == line.id }
                val translation = if (moving && position != null) center - position.offset - position.size / 2f else 0f
                Row(Modifier.fillMaxWidth().animateItem().zIndex(if (moving) 1f else 0f)
                    .graphicsLayer { translationY = translation; shadowElevation = if (moving) 8.dp.toPx() else 0f }
                    .background(if (moving) SaltTheme.colors.popup else androidx.compose.ui.graphics.Color.Transparent, SaltTheme.shapes.medium)
                    .lyricsSelection(line.id in state.selectedLineIds)
                    .then(if (dragging != null && drop?.key == line.id && !moving)
                        Modifier.border(1.dp, SaltTheme.colors.highlight, SaltTheme.shapes.medium) else Modifier)
                    .clickable(enabled = !state.busy && dragging == null) {
                        focus.requestFocus()
                        controller.selectLine(line.id, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp).height(48.dp).semantics { contentDescription = "拖动调整歌词顺序" }
                        .pointerInput(line.id, state.canEditRows, state.validating) {
                            if (state.canEditRows && !state.validating) detectDragGestures(
                                onDragStart = {
                                    focus.requestFocus()
                                    controller.selectLine(line.id)
                                    val item = scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == line.id }
                                    if (item != null) { center = item.offset + item.size / 2f; dragging = line.id }
                                },
                                onDrag = { change, amount -> change.consume(); center += amount.y },
                                onDragCancel = { dragging = null },
                                onDragEnd = {
                                    val target = scroll.layoutInfo.visibleItemsInfo.firstOrNull { center in it.offset.toFloat()..(it.offset + it.size).toFloat() }
                                        ?: scroll.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs(center - it.offset - it.size / 2f) }
                                    val id = target?.key as? Int
                                    if (id != null && id != line.id) controller.moveLine(line.id, id, center > target.offset + target.size / 2f)
                                    dragging = null
                                })
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
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
