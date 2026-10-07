package dev.gaboron.spwlyrics.integration.manualui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.codec.LyricsTimingOffset
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun ManualLyricsPreview(state: ManualSearchState, modifier: Modifier) {
    val list = rememberLazyListState()
    val lines = remember(state.document, state.delayValue, state.savedDelay) {
        state.document?.let { LyricsTimingOffset.shift(it, state.delayValue ?: state.savedDelay).lines }.orEmpty()
    }
    LaunchedEffect(state.selected) { list.scrollToItem(0) }
    Column(modifier.desktopPanel()) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("歌词预览", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            state.selected?.candidate?.let { candidate ->
                ElidedText(listOf(candidate.title, candidate.artists.joinToString(" / "), candidate.source.displayName)
                    .filter(String::isNotBlank).joinToString(" · "), color = DesktopColors.Muted, size = 11)
            }
        }
        DesktopDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.selected == null -> EmptyWorkspace("选择一个候选以预览歌词", glyph = DesktopGlyph.Music)
                state.previewing -> EmptyWorkspace("正在加载歌词预览", loading = true)
                lines.isEmpty() -> EmptyWorkspace("该候选没有可用歌词", glyph = DesktopGlyph.Music)
                else -> {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = list, contentPadding = PaddingValues(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(lines) { line ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    line.startMs?.let { Text(SpwLyricsEncoder.timestamp(it), color = DesktopColors.Muted, fontSize = 10.sp) }
                                    Text(line.text, fontSize = 14.sp, lineHeight = 22.sp)
                                    (line.translation?.takeIf(String::isNotBlank) ?: line.romanization?.takeIf(String::isNotBlank))?.let {
                                        Text(it, color = DesktopColors.Muted, fontSize = 12.sp, lineHeight = 19.sp)
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
                }
            }
        }
    }
}
