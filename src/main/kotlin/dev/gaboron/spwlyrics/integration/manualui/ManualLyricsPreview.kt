package dev.gaboron.spwlyrics.integration.manualui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.codec.LyricsTimingOffset
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsSection
import dev.gaboron.spwlyrics.integration.workspace.LyricsSectionTitle

@Composable
internal fun ManualLyricsPreview(state: ManualSearchState, modifier: Modifier) {
    val list = rememberLazyListState()
    val lines = remember(state.document, state.delayValue, state.savedDelay) {
        state.document?.let { LyricsTimingOffset.shift(it, state.delayValue ?: state.savedDelay).lines }.orEmpty()
    }
    LaunchedEffect(state.selected) { list.scrollToItem(0) }
    LyricsSection(modifier) {
        LyricsSectionTitle("歌词预览")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.selected == null -> EmptyWorkspace("选择一份歌词预览", "在搜索结果中选择歌曲", glyph = DesktopGlyph.Lyrics)
                state.previewing -> EmptyWorkspace("正在加载歌词", loading = true)
                lines.isEmpty() -> EmptyWorkspace("这份歌词没有可预览内容", glyph = DesktopGlyph.Lyrics)
                else -> {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
                            contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            items(lines) { line ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    line.startMs?.let { Text(SpwLyricsEncoder.timestamp(it), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub) }
                                    Text(line.text, fontSize = 18.sp, lineHeight = 28.sp)
                                    listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).forEach {
                                        Text(it, color = SaltTheme.colors.subText, lineHeight = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}
