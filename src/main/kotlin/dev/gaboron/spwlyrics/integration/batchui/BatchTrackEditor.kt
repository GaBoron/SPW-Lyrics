package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorController
import dev.gaboron.spwlyrics.integration.metadataui.MetadataFieldsPanel
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

/** Measure the fields at their final width; animate the pane and its gutter as one allocation. */
@Composable
internal fun BatchEditorPane(width: Dp, content: @Composable () -> Unit) {
    Layout(content = { if (width > 0.dp) content() }, modifier = Modifier.width(width).fillMaxHeight().clipToBounds()) { children, constraints ->
        val fieldWidth = 360.dp.roundToPx()
        val gutter = 16.dp.roundToPx()
        val child = children.singleOrNull()?.measure(Constraints.fixed(fieldWidth, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) { child?.placeRelative(gutter, 0) }
    }
}

@Composable
internal fun BatchTrackEditor(controller: MetadataEditorController, close: () -> Unit, modifier: Modifier) {
    val state = controller.state
    Column(modifier.onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.S) { controller.save(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { close(); true } else false
    }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.loaded?.path?.fileName?.toString() ?: "读取歌曲信息…", Modifier.weight(1f), maxLines = 1)
            DesktopIconButton("关闭歌曲编辑", DesktopGlyph.Exit, close, enabled = !state.busy)
        }
        LyricsStatus(if (state.dirty) "有未保存的修改。${state.status}" else state.status, state.error)
        MetadataFieldsPanel(controller, Modifier.weight(1f).fillMaxWidth())
        DesktopButton("写入歌曲文件", controller::save, Modifier.align(Alignment.End),
            enabled = state.canSave, primary = true, glyph = DesktopGlyph.Check)
    }
}
