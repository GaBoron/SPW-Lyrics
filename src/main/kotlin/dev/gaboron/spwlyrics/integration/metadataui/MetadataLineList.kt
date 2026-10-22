package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun MetadataLineList(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    val scroll = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    Box(modifier.focusRequester(focus).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.A) { controller.selectAllLines(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.Delete) { controller.deleteSelected(); true }
        else false
    }.focusable()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll, contentPadding = PaddingValues(4.dp)) {
            items(state.structured?.lines.orEmpty(), key = { it.id }) { original ->
                val line = state.line(original.id) ?: original
                Column(Modifier.fillMaxWidth().lyricsSelection(line.id in state.selectedLineIds).clickable(enabled = !state.busy) {
                        focus.requestFocus()
                        controller.selectLine(line.id, window.keyboardModifiers.isCtrlPressed, window.keyboardModifiers.isShiftPressed)
                    }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${line.id + 1}  ${line.start.ifBlank { "未定时" }}", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                    Text(state.lineText(line.id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(lyricVoiceLabel(state.vocalPart(line) == LyricVocalPart.DUET, line.background),
                        color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                }
                LyricsListDivider()
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
