package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.PlayingTimingWords
import dev.gaboron.spwlyrics.domain.TimingCursor
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

@Composable
internal fun TimingWordGrid(controller: LyricsTimingController, playing: PlayingTimingWords, follow: Boolean,
    focus: () -> Unit, modifier: Modifier, list: LazyListState = rememberLazyListState()) {
    val state = controller.state
    val target = if (follow && playing.follow != null) playing.follow else state.cursor
    LaunchedEffect(target?.lineId) {
        val index = state.lines.indexOfFirst { it.id == target?.lineId }
        if (index >= 0) list.animateScrollToItem(index)
    }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.lines, key = { _, line -> line.id }) { row, line ->
                val fill by animateColorAsState(when {
                    state.cursor?.lineId == line.id -> SaltTheme.colors.highlight.copy(alpha = .13f)
                    playing.cursors.any { it.lineId == line.id } -> SaltTheme.colors.highlight.copy(alpha = .035f)
                    else -> Color.Transparent
                }, tween(160), label = "timing line")
                RoundedColumn(Modifier.animateItem().animateContentSize(tween(180)), paddingValues = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth().background(fill).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((row + 1).toString(), Modifier.width(40.dp).padding(start = 12.dp),
                            style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (line.words.isEmpty()) Column(Modifier.padding(12.dp)) {
                                Text(line.text)
                                LyricsStatus(if (state.busy || state.preparing) "正在准备逐字内容…" else "打开打轴设置，准备自动分词。")
                            }
                            line.words.forEachIndexed { index, word ->
                                if (word.text.isNotBlank()) key(line.id, index) {
                                    val cursor = TimingCursor(line.id, index)
                                    val bring = remember { BringIntoViewRequester() }
                                    LaunchedEffect(target == cursor) { if (target == cursor) bring.bringIntoView() }
                                    TimingWordCell(word, state.cursor == cursor, cursor in playing.cursors, !state.busy,
                                        { controller.choose(cursor); focus() }, Modifier.bringIntoViewRequester(bring))
                                }
                            }
                        }
                        Column(Modifier.width(108.dp).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            TimingTimeLabel(line.start, start = true)
                            AnimatedVisibility(line.id in state.skipped, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Text("已跳过", style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
                            }
                            TimingTimeLabel(line.end, start = false)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
