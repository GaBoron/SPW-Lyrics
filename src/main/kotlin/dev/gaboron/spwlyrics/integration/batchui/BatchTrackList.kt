package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.application.LyricsBatchItem
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun BatchTrackList(state: BatchProcessingState, select: (String) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    var cursor by remember { mutableStateOf(-1) }
    LaunchedEffect(cursor) {
        if (cursor >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == cursor }) list.scrollToItem(cursor)
    }
    Column(modifier.desktopPanel()) {
        Row(Modifier.fillMaxWidth().height(42.dp).background(DesktopColors.Subtle).padding(start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(30.dp))
            Text("歌曲", Modifier.weight(1.4f), color = DesktopColors.Muted, fontSize = 11.sp)
            Text("歌手 / 专辑", Modifier.weight(1.3f), color = DesktopColors.Muted, fontSize = 11.sp)
            Text("状态", Modifier.width(74.dp), color = DesktopColors.Muted, fontSize = 11.sp)
            Text("进度", Modifier.width(68.dp), color = DesktopColors.Muted, fontSize = 11.sp)
            Text("阶段 / 结果", Modifier.weight(1.6f), color = DesktopColors.Muted, fontSize = 11.sp)
        }
        DesktopDivider()
        Box(Modifier.weight(1f).fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || state.keys.isEmpty()) false else when (event.key) {
                Key.DirectionDown -> { cursor = (cursor + 1).coerceAtMost(state.keys.lastIndex); true }
                Key.DirectionUp -> { cursor = (cursor - 1).coerceIn(0, state.keys.lastIndex); true }
                Key.Spacebar -> { state.keys.getOrNull(cursor)?.let(select); true }
                else -> false
            }
        }.focusable()) {
            if (state.snapshot.items.isEmpty()) EmptyWorkspace(if (state.busy) "正在读取音乐库" else "音乐库中暂无歌曲",
                if (state.busy) "" else "完成 SPW 扫描后重新读取音乐库", loading = state.busy, glyph = DesktopGlyph.Library)
            else {
                LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = list) {
                    itemsIndexed(state.snapshot.items) { index, item ->
                        BatchRow(item, state.keys[index] in state.selectedKeys, state.editable, cursor == index,
                            onClick = { focus.requestFocus(); cursor = index; select(state.keys[index]) })
                        DesktopDivider()
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
            }
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${state.snapshot.items.size} 首歌曲", color = DesktopColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("↑ ↓ 移动 · 空格勾选", color = DesktopColors.Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BatchRow(item: LyricsBatchItem, checked: Boolean, enabled: Boolean, focused: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().height(52.dp).background(if (focused) DesktopColors.Selected else if (hovered) DesktopColors.Hover else DesktopColors.Surface)
        .hoverable(interaction).clickable(interaction, null, onClick = onClick).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DesktopCheck(checked, onClick, enabled, modifier = Modifier.width(30.dp))
        ElidedText(item.query.title, Modifier.weight(1.4f).padding(end = 8.dp))
        ElidedText(listOf(item.query.artists.joinToString(" / "), item.query.album).filter(String::isNotBlank).joinToString(" · "),
            Modifier.weight(1.3f).padding(end = 8.dp), color = DesktopColors.Muted)
        Box(Modifier.width(74.dp)) {
            Text(when (item.state) {
                LyricsBatchItemState.WAITING -> "等待处理"
                LyricsBatchItemState.SEARCHING -> "正在搜索"
                LyricsBatchItemState.COMPLETED -> "已完成"
                LyricsBatchItemState.FAILED -> "需要重试"
                LyricsBatchItemState.CACHED -> "已有缓存"
                LyricsBatchItemState.EXCLUDED -> "未选择"
                LyricsBatchItemState.CANCELLED -> "已停止"
            }, fontSize = 11.sp, color = when (item.state) {
                LyricsBatchItemState.FAILED -> DesktopColors.Error
                LyricsBatchItemState.SEARCHING, LyricsBatchItemState.COMPLETED -> DesktopColors.Accent
                else -> DesktopColors.Muted
            })
        }
        Column(Modifier.width(68.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${(item.progress * 100).toInt()}%", fontSize = 10.sp, color = DesktopColors.Muted)
            Box(Modifier.width(50.dp).height(3.dp).background(DesktopColors.Border)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress.toFloat().coerceIn(0f, 1f)).background(DesktopColors.Accent.copy(alpha = .6f)))
            }
        }
        Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ElidedText(item.stage, size = 11)
            ElidedText(listOf(item.source, item.quality).filter(String::isNotBlank).joinToString(" · ").ifBlank { item.message },
                color = DesktopColors.Muted, size = 10)
        }
    }
}
