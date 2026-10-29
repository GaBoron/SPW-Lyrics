package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun ManualCandidateList(state: ManualSearchState, select: (CandidateScore) -> Unit, selectSort: (CandidateColumn) -> Unit, modifier: Modifier) {
    val sort = state.sort
    val rows = remember(state.candidates, state.automatic, sort) {
        sort.rows(state.candidates, state.automatic)
    }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val selectedIndex = rows.indexOfFirst { row -> state.selected?.let { sameCandidate(it.candidate, row.candidate) } == true }
    LaunchedEffect(state.selected, rows) {
        if (selectedIndex >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) list.animateScrollToItem(selectedIndex)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("搜索结果  ${rows.size}" + if (state.matchingAutomatic) " · 正在自动搜索…" else "",
                Modifier.weight(1f), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            LyricsMenu("排序：${sort.title}", CandidateColumn.entries.toList(), sort, { it.title }, selectSort)
        }
        LyricsSection(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || rows.isEmpty() || state.mutating) false else {
                    val next = when (event.key) {
                        Key.DirectionDown -> (selectedIndex + 1).coerceAtMost(rows.lastIndex)
                        Key.DirectionUp -> (selectedIndex - 1).coerceAtLeast(0)
                        Key.MoveHome -> 0
                        Key.MoveEnd -> rows.lastIndex
                        else -> -1
                    }
                    if (next >= 0) { select(rows[next]); true } else false
                }
            }.focusable()) {
                when {
                    state.readingTrack || rows.isEmpty() && (state.searching || state.matchingAutomatic) -> EmptyWorkspace("正在查找歌词", loading = true)
                    rows.isEmpty() -> EmptyWorkspace(if (state.searched) "没有找到相关歌词" else "搜索你正在听的歌",
                        if (state.searched) "缩短关键词，或选择其他来源重试" else "选择结果后即可预览、应用或写入歌曲")
                    else -> {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = list, contentPadding = PaddingValues(4.dp)) {
                            items(rows) { row ->
                                ManualCandidateRow(row, row == rows.getOrNull(selectedIndex), !state.mutating, state.isAutomatic(row)) {
                                    focus.requestFocus(); select(row)
                                }
                                LyricsListDivider()
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}
