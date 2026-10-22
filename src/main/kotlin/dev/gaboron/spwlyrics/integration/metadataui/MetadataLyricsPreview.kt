package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLyricsPreview(state: MetadataEditorState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.preview) { list.scrollToItem(0) }
    when {
        state.validating -> EmptyWorkspace("正在解析歌词", loading = true)
        state.previewError != null -> EmptyWorkspace("歌词无法预览", "回到编辑原文检查格式", glyph = DesktopGlyph.Lyrics)
        state.preview == null -> EmptyWorkspace("尚无内嵌歌词", "导入歌词文件或从搜索结果写入", glyph = DesktopGlyph.Lyrics)
        else -> Box(Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
                    contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    items(state.preview.lines) { line ->
                        val duet = !line.agent.isNullOrBlank() && line.agent != state.previewMainAgent
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (duet) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                line.startMs?.let { Text(SpwLyricsEncoder.timestamp(it), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub) }
                                Text(if (duet) "对唱 · 右" else "主唱 · 左", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                if (line.background) Text("背景人声", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            }
                            Text(line.text, fontSize = 18.sp, lineHeight = 28.sp, modifier = Modifier.fillMaxWidth(),
                                textAlign = if (duet) TextAlign.End else TextAlign.Start)
                            listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).forEach {
                                Text(it, color = SaltTheme.colors.subText, modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (duet) TextAlign.End else TextAlign.Start)
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
