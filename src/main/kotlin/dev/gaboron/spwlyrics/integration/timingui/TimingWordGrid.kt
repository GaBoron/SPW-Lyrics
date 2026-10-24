package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun TimingWordGrid(controller: LyricsTimingController, focus: () -> Unit, modifier: Modifier) {
    val state = controller.state
    val list = rememberLazyListState()
    LaunchedEffect(state.cursor?.lineId) {
        val index = state.lines.indexOfFirst { it.id == state.cursor?.lineId }
        if (index >= 0) list.animateScrollToItem(index)
    }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(4.dp)) {
        items(state.lines, key = { it.id }) { line ->
            Column(Modifier.fillMaxWidth().animateItem().animateContentSize(tween(180)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("第 ${line.id + 1} 句${if (line.id in state.skipped) " · 已跳过" else ""}")
                Text(line.text)
                if (line.words.isEmpty()) LyricsStatus("暂无逐字内容；可在“打轴设置与分词”中准备逐字。")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    line.words.forEachIndexed { index, word ->
                        if (word.text.isNotBlank()) key(line.id, index) {
                            val cursor = TimingCursor(line.id, index)
                            val active = state.cursor == cursor
                            val bring = remember { BringIntoViewRequester() }
                            LaunchedEffect(active) { if (active) bring.bringIntoView() }
                            DesktopButton(word.text.take(40), { controller.choose(cursor); focus() },
                                Modifier.bringIntoViewRequester(bring), enabled = !state.busy, primary = active)
                        }
                    }
                }
            }
        }
    }
}
