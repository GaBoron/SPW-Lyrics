package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLyricsPanel(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    var view by remember { mutableStateOf(0) }
    Column(modifier.desktopPanel()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("内嵌歌词", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            state.preview?.let { QuietTag("${it.lines.size} 行") }
            DesktopButton("搜索当前歌曲", controller::openSearch, enabled = !state.busy)
            DesktopButton("导入歌词文件", controller::importLyrics, enabled = state.loaded != null && !state.busy)
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("转换为", fontSize = 12.sp, color = DesktopColors.Muted)
            MetadataFormatPicker(state.format, controller::format, enabled = !state.busy)
            DesktopButton("转换", controller::convert, enabled = state.loaded != null && !state.busy && !state.validating && state.preview != null)
            Text(state.format.detail, fontSize = 11.sp, lineHeight = 16.sp, color = DesktopColors.Muted, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopButton("编辑歌词", { view = 0 }, primary = view == 0)
            DesktopButton("编辑原文", { view = 1 }, primary = view == 1)
            DesktopButton("歌词预览", { view = 2 }, primary = view == 2)
            Spacer(Modifier.weight(1f))
            Text("${state.draft.lyrics.length} 字符", color = DesktopColors.Muted, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (view == 0) MetadataStructuredEditor(controller)
            else if (view == 2) MetadataLyricsPreview(state)
            else MetadataTextArea(state.draft.lyrics, controller::lyrics, Modifier.fillMaxSize(),
                enabled = state.loaded != null && !state.busy, invalid = state.previewError != null,
                placeholder = "读取歌曲后，可编辑或导入 TTML、LRC、增强 LRC、SPL。也可在手动搜索中选中候选，点击“导入元数据…”。")
        }
        Text(state.previewError ?: if (state.validating) "正在解析歌词…"
            else "修改自动进入草稿，点击“写入歌曲文件”保存。逐句编辑保留 TTML 扩展；清空原文可清除内嵌歌词。",
            color = if (state.previewError != null) DesktopColors.Error else DesktopColors.Muted,
            fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(16.dp, 10.dp))
    }
}

@Composable
private fun MetadataLyricsPreview(state: MetadataEditorState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.preview) { list.scrollToItem(0) }
    when {
        state.validating -> EmptyWorkspace("正在解析歌词", loading = true)
        state.previewError != null -> EmptyWorkspace("歌词无法预览", "请回到编辑原文检查格式。", glyph = DesktopGlyph.Music)
        state.preview == null -> EmptyWorkspace("没有内嵌歌词", "导入歌词文件或粘贴歌词后即可预览。", glyph = DesktopGlyph.Music)
        else -> Box(Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
                    contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.preview.lines) { line ->
                        val duet = !line.agent.isNullOrBlank() && line.agent != state.previewMainAgent
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (duet) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                line.startMs?.let { Text(SpwLyricsEncoder.timestamp(it), color = DesktopColors.Muted, fontSize = 10.sp) }
                                QuietTag(if (duet) "对唱 · 右" else "主唱 · 左")
                                if (line.background) QuietTag("背景人声")
                            }
                            Text(line.text, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth(),
                                textAlign = if (duet) TextAlign.End else TextAlign.Start)
                            listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).forEach {
                                Text(it, color = DesktopColors.Muted, fontSize = 12.sp, lineHeight = 19.sp,
                                    modifier = Modifier.fillMaxWidth(), textAlign = if (duet) TextAlign.End else TextAlign.Start)
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
