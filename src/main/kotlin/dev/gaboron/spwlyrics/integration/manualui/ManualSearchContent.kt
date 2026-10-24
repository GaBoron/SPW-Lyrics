package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun ManualSearchContent(controller: ManualSearchController) {
    val state = controller.state
    val searchFocus = remember { FocusRequester() }
    var detail by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when {
            it.isCtrlPressed && it.key == Key.F -> { searchFocus.requestFocus(); true }
            it.isCtrlPressed && it.key == Key.Enter -> { controller.apply(); true }
            it.key == Key.F5 -> { controller.refreshTrack(); true }
            else -> false
        }
    }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ManualSearchToolbar(controller, searchFocus)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ManualCandidateList(state, { controller.select(it); detail = true }, Modifier.weight(1f).fillMaxHeight())
                ManualPreviewContent(controller, Modifier.weight(1.05f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricsTabs(listOf("搜索结果", "歌词预览"), if (detail) 1 else 0, { detail = it == 1 })
                LyricsTransition(detail, Modifier.weight(1f).fillMaxWidth()) { preview ->
                    if (preview) ManualPreviewContent(controller, Modifier.fillMaxSize())
                    else ManualCandidateList(state, { controller.select(it); detail = true }, Modifier.fillMaxSize())
                }
            }
        }
        LyricsActionBar {
            DesktopButton("恢复自动匹配", controller::useAutomatic, enabled = state.canMutate)
            DesktopButton("切回本地歌词", controller::useLocal, enabled = state.canMutate)
            DesktopButton("导入元数据", controller::editMetadata, enabled = !state.busy && state.selected != null)
            DesktopButton("应用歌词", controller::apply, enabled = state.canMutate && state.document != null,
                primary = true, glyph = DesktopGlyph.Check)
        }
    }
}

@Composable
private fun ManualPreviewContent(controller: ManualSearchController, modifier: Modifier) {
    val state = controller.state
    BoxWithConstraints(modifier) {
        val metadataHeight = ((maxHeight - 160.dp) / 2).coerceIn(48.dp, 240.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ManualLyricsPreview(state, Modifier.weight(1f).fillMaxWidth())
            ManualMetadataViewer(state, metadataHeight)
            ManualDelayEditor(state, controller)
        }
    }
}
