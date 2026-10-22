package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun ManualCandidateList(state: ManualSearchState, select: (CandidateScore) -> Unit, modifier: Modifier) {
    var sort by rememberSaveable { mutableStateOf(CandidateColumn.SCORE) }
    val rows = remember(state.candidates, sort) {
        state.candidates.sortedWith(if (sort == CandidateColumn.SCORE) sort.comparator().reversed() else sort.comparator())
    }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val selectedIndex = rows.indexOf(state.selected)
    LaunchedEffect(state.selected, rows) {
        if (selectedIndex >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) list.scrollToItem(selectedIndex)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("搜索结果  ${rows.size}", Modifier.weight(1f), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            LyricsMenu("排序：${sort.title}", CandidateColumn.entries.toList(), sort, { it.title }, { sort = it })
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
                    state.searching || state.readingTrack -> EmptyWorkspace("正在查找歌词", loading = true)
                    rows.isEmpty() -> EmptyWorkspace(if (state.searched) "没有找到相关歌词" else "搜索你正在听的歌",
                        if (state.searched) "缩短关键词，或选择其他来源重试" else "选择结果后即可预览、应用或写入歌曲")
                    else -> {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = list) {
                            items(rows) { row ->
                                CandidateRow(row, row == state.selected, !state.mutating) { focus.requestFocus(); select(row) }
                                ItemDivider()
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(row: CandidateScore, selected: Boolean, enabled: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) SaltTheme.colors.highlight.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = click).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.candidate.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(row.candidate.artists.joinToString(" / "), row.candidate.album).filter(String::isNotBlank).joinToString(" · "),
                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${CandidateColumn.SOURCE.text(row)}  ·  ${CandidateColumn.QUALITY.text(row)}  ·  匹配 ${CandidateColumn.SCORE.text(row)}",
                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(CandidateColumn.DURATION.text(row), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            if (selected) DesktopIcon(DesktopGlyph.Check, SaltTheme.colors.highlight)
        }
    }
}
