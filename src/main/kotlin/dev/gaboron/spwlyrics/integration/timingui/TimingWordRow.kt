package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.application.PlayingTimingWords
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus
import kotlin.math.roundToInt

/** Center only the requested word, clamping naturally at either end of the row. */
@Composable
internal fun TimingWordRow(line: EditableLyricLine, state: TimingEditorState, target: TimingCursor?,
    playing: PlayingTimingWords, choose: (TimingCursor) -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    val bounds = remember(line.id) { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var width by remember { mutableIntStateOf(0) }
    val word = target?.takeIf { it.lineId == line.id }?.word
    val location = bounds[word]
    LaunchedEffect(word, location, width, scroll.maxValue) {
        if (location != null && width > 0 && scroll.maxValue != Int.MAX_VALUE) {
            val center = location.first + location.second / 2
            scroll.animateScrollTo((center - width / 2).coerceIn(0, scroll.maxValue), tween(110))
        }
    }
    Row(modifier.onSizeChanged { width = it.width }.horizontalScroll(scroll).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (line.words.isEmpty()) Column(Modifier.padding(12.dp)) {
            Text(line.text)
            LyricsStatus(if (state.busy || state.preparing) "正在准备逐字内容…" else "点击上方“自动分词”准备逐字内容。")
        }
        line.words.forEachIndexed { index, value ->
            if (value.text.isNotBlank()) key(line.id, index) {
                val cursor = TimingCursor(line.id, index)
                TimingWordCell(value, state.cursor == cursor, cursor in playing.cursors, !state.busy, { choose(cursor) },
                    Modifier.onGloballyPositioned {
                        val next = it.positionInParent().x.roundToInt() to it.size.width
                        if (bounds[index] != next) bounds[index] = next
                    })
            }
        }
    }
}
