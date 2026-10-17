package dev.gaboron.spwlyrics.integration.manualui

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.integration.composeui.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ManualCandidateList(state: ManualSearchState, select: (CandidateScore) -> Unit, modifier: Modifier) {
    var sort by remember { mutableStateOf<CandidateColumn?>(null) }
    var descending by remember { mutableStateOf(false) }
    val rows = remember(state.candidates, sort, descending) {
        sort?.let { state.candidates.sortedWith(if (descending) it.comparator().reversed() else it.comparator()) } ?: state.candidates
    }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val selectedIndex = rows.indexOf(state.selected)
    LaunchedEffect(state.selected, rows) {
        if (selectedIndex >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) list.scrollToItem(selectedIndex)
    }
    Column(modifier.desktopPanel()) {
        Row(Modifier.fillMaxWidth().height(42.dp).background(DesktopColors.Subtle).padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CandidateColumn.entries.forEach { column ->
                val cell = if (column.weight > 0) Modifier.weight(column.weight) else Modifier.width(column.width.dp)
                Box(cell.clickable {
                    if (sort == column) descending = !descending else { sort = column; descending = false }
                }.padding(vertical = 10.dp)) {
                    Text(column.title + if (sort == column) if (descending) " ↓" else " ↑" else "",
                        color = DesktopColors.Muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        DesktopDivider()
        Box(Modifier.weight(1f).fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || rows.isEmpty() || state.mutating) false
            else {
                val next = when (event.key) {
                    Key.DirectionDown -> (selectedIndex + 1).coerceAtMost(rows.lastIndex)
                    Key.DirectionUp -> (if (selectedIndex < 0) 0 else selectedIndex - 1).coerceAtLeast(0)
                    Key.MoveHome -> 0
                    Key.MoveEnd -> rows.lastIndex
                    else -> -1
                }
                if (next >= 0) { select(rows[next]); true } else false
            }
        }.focusable()) {
            when {
                state.searching || state.readingTrack -> EmptyWorkspace(if (state.readingTrack) "正在读取当前歌曲" else "正在搜索", loading = true)
                rows.isEmpty() -> EmptyWorkspace(if (state.searched) "没有找到相关结果" else "搜索歌曲以查找歌词",
                    if (state.searched) "试试更短的关键词或其他来源" else "搜索结果将在这里显示")
                else -> {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = list) {
                        itemsIndexed(rows) { _, row ->
                            CandidateRow(row, row == state.selected, enabled = !state.mutating,
                                onClick = { focus.requestFocus(); select(row) })
                            DesktopDivider()
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
                }
            }
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.searching) "搜索中…" else "找到 ${rows.size} 个候选", color = DesktopColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("↑ ↓ 选择 · Ctrl+Enter 应用", color = DesktopColors.Muted, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateRow(row: CandidateScore, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().height(50.dp).background(when {
        selected -> DesktopColors.Selected
        hovered -> DesktopColors.Hover
        else -> DesktopColors.Surface
    }).hoverable(interaction, enabled).combinedClickable(interaction, null, enabled,
        onDoubleClick = onClick, onClick = onClick).semantics { this.selected = selected }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) DesktopColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
        Row(Modifier.weight(1f).padding(start = 11.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CandidateColumn.entries.forEach { column ->
                val cell = if (column.weight > 0) Modifier.weight(column.weight) else Modifier.width(column.width.dp)
                Box(cell.padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                    when (column) {
                        CandidateColumn.SOURCE -> QuietTag(column.text(row))
                        CandidateColumn.QUALITY -> QuietTag(column.text(row), selected)
                        CandidateColumn.SCORE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(column.text(row), color = DesktopColors.Accent, fontSize = 11.sp)
                            Box(Modifier.width(42.dp).height(3.dp).background(DesktopColors.Border)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(row.score.toFloat().coerceIn(0f, 1f)).background(DesktopColors.Accent.copy(alpha = .65f)))
                            }
                        }
                        else -> ElidedText(column.text(row), color = if (column == CandidateColumn.TITLE) DesktopColors.Text else DesktopColors.Muted)
                    }
                }
            }
        }
    }
}
