package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.PlayingTimingWords

@Composable
internal fun TimingWordGrid(controller: LyricsTimingController, playing: PlayingTimingWords, follow: Boolean,
    focus: () -> Unit, modifier: Modifier, list: LazyListState = rememberLazyListState()) {
    val state = controller.state
    val target = if (follow && playing.follow != null) playing.follow else state.cursor
    val height = list.layoutInfo.viewportSize.height
    LaunchedEffect(target?.lineId, height) {
        val index = state.lines.indexOfFirst { it.id == target?.lineId }
        if (index >= 0 && height > 0) {
            val viewport = list.layoutInfo
            val visible = viewport.visibleItemsInfo.firstOrNull { it.index == index }
            val rowHeight = visible?.size ?: viewport.visibleItemsInfo.firstOrNull()?.size ?: 100
            list.animateScrollToItem(index, -((height - rowHeight) / 2).coerceAtLeast(0))
        }
    }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.lines, key = { _, line -> line.id }) { row, line ->
                val selected = state.cursor?.lineId == line.id
                val active = playing.cursors.any { it.lineId == line.id }
                val selection by animateFloatAsState(if (selected) .13f else 0f, tween(if (selected) 24 else 100), label = "selected line")
                val activity by animateFloatAsState(if (active) .035f else 0f, tween(if (active) 20 else 90), label = "playing line")
                val fill = SaltTheme.colors.highlight.copy(alpha = maxOf(selection, activity))
                RoundedColumn(Modifier.animateItem().animateContentSize(tween(180)), paddingValues = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth().background(fill).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((row + 1).toString(), Modifier.width(40.dp).padding(start = 12.dp),
                            style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
                        TimingWordRow(line, state, target, playing, { controller.choose(it); focus() }, Modifier.weight(1f))
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
