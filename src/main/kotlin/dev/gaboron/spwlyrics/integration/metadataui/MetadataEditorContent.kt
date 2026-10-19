package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataEditorContent(controller: MetadataEditorController) {
    val state = controller.state
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(DesktopColors.Background).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.S) { controller.save(); true }
        else if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.reload(); true }
        else false
    }) {
        Column(Modifier.weight(1f).padding(24.dp, 20.dp, 24.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("歌曲元数据", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        if (state.dirty) QuietTag("未保存", accent = true)
                    }
                    ElidedText(state.loaded?.path?.toString() ?: "直接编辑本地歌曲的信息与内嵌歌词", size = 12)
                }
                DesktopButton("打开歌曲文件", controller::openFile, enabled = !state.busy, glyph = DesktopGlyph.Library)
                DesktopButton("读取当前歌曲", controller::current, enabled = !state.busy,
                    loading = state.busy, glyph = DesktopGlyph.Refresh)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataFieldsPanel(controller, Modifier.width(280.dp).fillMaxHeight())
                MetadataLyricsPanel(controller, Modifier.weight(1f).fillMaxHeight())
            }
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().background(DesktopColors.Subtle).padding(24.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(state.status, color = if (state.error) DesktopColors.Error else DesktopColors.Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Text("高级编辑前往AMLL TTML TOOL", color = DesktopColors.Accent, fontSize = 11.sp,
                    modifier = Modifier.clickable { uriHandler.openUri("https://amll-ttml-tool.stevexmh.net/") })
            }
            DesktopButton("重新读取", controller::reload, enabled = state.loaded != null && !state.busy)
            DesktopButton("写入歌曲文件", controller::save, enabled = state.canSave,
                primary = true, glyph = DesktopGlyph.Check)
        }
    }
    if (state.pendingDiscard != null) AlertDialog(
        onDismissRequest = controller::cancelDiscard,
        title = { Text("放弃未保存的修改？") },
        text = { Text("修改尚未写入歌曲文件。返回后可点击“写入歌曲文件”保存。") },
        confirmButton = { DesktopButton("放弃修改", controller::discard) },
        dismissButton = { DesktopButton("继续编辑", controller::cancelDiscard, primary = true) },
    )
}

@Composable
private fun MetadataFieldsPanel(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    val scroll = rememberScrollState()
    Column(modifier.desktopPanel()) {
        Text("歌曲信息", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 15.dp))
        DesktopDivider()
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(end = 8.dp).verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AudioMetadataField.entries.forEach { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(field.label, fontSize = 12.sp, color = DesktopColors.Muted)
                        MetadataTextArea(state.draft[field], { controller.field(field, it) }, Modifier.fillMaxWidth().height(58.dp),
                            enabled = state.loaded != null && !state.busy)
                    }
                }
                Text("多值标签每行填写一个值。保存时保留封面和未编辑的其他标签；失败时回退原文件。",
                    color = DesktopColors.Muted, fontSize = 11.sp, lineHeight = 18.sp)
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
        }
    }
}
