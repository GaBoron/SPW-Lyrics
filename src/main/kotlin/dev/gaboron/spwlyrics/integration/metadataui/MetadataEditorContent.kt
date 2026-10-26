package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.timingui.LyricsTimingController

@Composable
internal fun MetadataEditorContent(controller: MetadataEditorController, timing: LyricsTimingController,
    view: MetadataLyricsView, selectView: (MetadataLyricsView) -> Unit) {
    val state = controller.state
    val focused = view == MetadataLyricsView.TIMING
    var page by rememberSaveable { mutableStateOf(0) }
    var metadataExpanded by rememberSaveable { mutableStateOf(true) }
    val gap by animateDpAsState(if (focused) 4.dp else 16.dp, tween(220), label = "editor density")
    val paneGap by animateDpAsState(if (focused) 0.dp else 12.dp, tween(220), label = "editor pane gap")
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.S) { controller.save(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.reload(); true }
        else false
    }, verticalArrangement = Arrangement.spacedBy(gap)) {
        AnimatedVisibility(!focused, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DesktopButton("打开歌曲", controller::openFile, enabled = !state.busy, glyph = DesktopGlyph.Library)
                    DesktopButton("读取当前歌曲", controller::current, enabled = !state.busy, loading = state.busy)
                    DesktopButton("重新读取", controller::reload, enabled = state.loaded != null && !state.busy, glyph = DesktopGlyph.Refresh)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MetadataContentTransition(state.loaded?.path?.toString() ?: "打开歌曲，编辑信息与内嵌歌词") { path ->
                        Text(path, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    MetadataContentTransition((if (state.dirty) "有未保存的修改。${state.status}" else state.status) to state.error) { (message, error) ->
                        LyricsStatus(message, error)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 1000.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(paneGap)) {
                AnimatedVisibility(!wide && !focused, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    LyricsTabs(listOf("歌曲信息", "内嵌歌词"), page, { page = it })
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedVisibility(wide && !focused, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Row {
                            MetadataSidebar(controller, metadataExpanded, { metadataExpanded = it }, Modifier.fillMaxHeight())
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (wide || focused) {
                            MetadataLyricsPanel(controller, timing, view, { page = 1; selectView(it) }, Modifier.fillMaxSize())
                        } else LyricsPageTransition(page, { it }, Modifier.fillMaxSize()) { selected ->
                            if (selected == 0) MetadataFieldsPanel(controller, Modifier.fillMaxSize())
                            else MetadataLyricsPanel(controller, timing, view, { page = 1; selectView(it) }, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        MetadataEditorActions(controller, focused)
    }
}

@Composable
internal fun MetadataDiscardDialog(controller: MetadataEditorController) {
    val state = controller.state
    if (state.pendingDiscard == null) return
    DesktopDialog(controller::cancelDiscard) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("有未保存的修改")
            Text(state.loaded?.path?.fileName?.toString().orEmpty())
            Text("保存后继续，或放弃本次修改；继续编辑会取消离开。")
            (state.previewError ?: state.status.takeIf { state.error })?.let { LyricsStatus(it, true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopButton("保存并继续", controller::saveAndContinue, enabled = state.canSaveAndContinue,
                    loading = state.busy, primary = true)
                DesktopButton("放弃修改", controller::discard, enabled = !state.busy)
                DesktopButton("继续编辑", controller::cancelDiscard, enabled = !state.busy)
            }
        }
    }
}
