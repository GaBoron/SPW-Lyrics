package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.dialog.BasicDialog
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun MetadataEditorContent(controller: MetadataEditorController) {
    val state = controller.state
    var page by rememberSaveable { mutableStateOf(0) }
    var metadataExpanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.S) { controller.save(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.reload(); true }
        else false
    }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("打开歌曲", controller::openFile, enabled = !state.busy, glyph = DesktopGlyph.Library)
            DesktopButton("读取当前歌曲", controller::current, enabled = !state.busy, loading = state.busy)
            DesktopButton("重新读取", controller::reload, enabled = state.loaded != null && !state.busy, glyph = DesktopGlyph.Refresh)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.loaded?.path?.toString() ?: "打开歌曲，编辑信息与内嵌歌词", maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            LyricsStatus(if (state.dirty) "有未保存的修改。${state.status}" else state.status, state.error)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MetadataSidebar(controller, metadataExpanded, { metadataExpanded = it }, Modifier.fillMaxHeight())
                MetadataLyricsPanel(controller, Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricsTabs(listOf("歌曲信息", "内嵌歌词"), page, { page = it })
                LyricsTransition(page, Modifier.weight(1f).fillMaxWidth()) { section ->
                    if (section == 0) MetadataFieldsPanel(controller, Modifier.fillMaxSize())
                    else MetadataLyricsPanel(controller, Modifier.fillMaxSize())
                }
            }
        }
        MetadataEditorActions(controller)
    }
}

@Composable
internal fun MetadataDiscardDialog(controller: MetadataEditorController) {
    if (controller.state.pendingDiscard == null) return
    BasicDialog(onDismissRequest = controller::cancelDiscard) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("放弃未保存的修改？")
            Text("修改尚未写入歌曲文件。返回后可点击“写入歌曲文件”保存。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopButton("放弃修改", controller::discard)
                DesktopButton("继续编辑", controller::cancelDiscard, primary = true)
            }
        }
    }
}
