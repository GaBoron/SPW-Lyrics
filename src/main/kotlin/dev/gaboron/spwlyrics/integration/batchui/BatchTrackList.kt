package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.LyricsBatchItem
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsSection
import dev.gaboron.spwlyrics.integration.workspace.LyricsListDivider
import dev.gaboron.spwlyrics.integration.workspace.lyricsSelection

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun BatchTrackList(state: BatchProcessingState, select: (String) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val scrollbar = remember { MutableInteractionSource() }
    val following = rememberBatchAutoFollow(state, list, scrollbar)
    var cursor by remember { mutableStateOf(-1) }
    LaunchedEffect(cursor) {
        if (cursor in state.keys.indices && list.layoutInfo.visibleItemsInfo.none { it.index == cursor }) list.animateScrollToItem(cursor)
    }
    LyricsSection(modifier) {
        Box(Modifier.fillMaxSize().then(following.modifier).focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || state.keys.isEmpty()) false else {
              if (event.key in listOf(Key.DirectionDown, Key.DirectionUp, Key.PageDown, Key.PageUp, Key.MoveHome, Key.MoveEnd, Key.Spacebar)) following.userInput()
              when (event.key) {
                Key.DirectionDown -> { cursor = (cursor + 1).coerceAtMost(state.keys.lastIndex); true }
                Key.DirectionUp -> { cursor = (cursor - 1).coerceIn(0, state.keys.lastIndex); true }
                Key.Spacebar -> if (state.editable) { state.keys.getOrNull(cursor)?.let(select); true } else false
                else -> false
              }
            }
        }.focusable()) {
            if (state.snapshot.items.isEmpty()) EmptyWorkspace(if (state.busy) "正在读取音乐库" else "音乐库中暂无歌曲",
                if (state.busy) "" else "在 SPW 扫描歌曲后，点击刷新音乐库", loading = state.busy, glyph = DesktopGlyph.Library)
            else {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list, contentPadding = PaddingValues(4.dp)) {
                    itemsIndexed(state.snapshot.items, key = { index, _ -> "$index:${state.keys[index]}" }) { index, item ->
                        Row(Modifier.fillMaxWidth().lyricsSelection(cursor == index || item.state == LyricsBatchItemState.SEARCHING),
                            verticalAlignment = Alignment.CenterVertically) {
                            ItemCheck(state.keys[index] in state.selectedKeys, {
                                focus.requestFocus(); cursor = index; select(state.keys[index])
                            }, item.query.title, Modifier.weight(1f), enabled = state.editable,
                                sub = item.query.artists.joinToString(" / ").ifBlank { item.query.album })
                            BatchItemStatus(item, Modifier.widthIn(max = 180.dp).padding(end = 16.dp))
                        }
                        LyricsListDivider()
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight(), interactionSource = scrollbar)
            }
        }
    }
}

@Composable
private fun BatchItemStatus(item: LyricsBatchItem, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(when (item.state) {
            LyricsBatchItemState.WAITING -> "等待处理"
            LyricsBatchItemState.SEARCHING -> "搜索中 ${(item.progress * 100).toInt()}%"
            LyricsBatchItemState.COMPLETED -> "已缓存"
            LyricsBatchItemState.FAILED -> "需要重试"
            LyricsBatchItemState.CACHED -> "已有缓存"
            LyricsBatchItemState.EXCLUDED -> "未选择"
            LyricsBatchItemState.CANCELLED -> "已停止"
        }, style = SaltTheme.textStyles.sub, color = when (item.state) {
            LyricsBatchItemState.FAILED -> SaltTheme.colors.error
            LyricsBatchItemState.SEARCHING, LyricsBatchItemState.COMPLETED -> SaltTheme.colors.highlight
            else -> SaltTheme.colors.subText
        })
        Text(listOf(item.source, item.quality).filter(String::isNotBlank).joinToString(" · ").ifBlank { item.stage.ifBlank { item.message } },
            color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
