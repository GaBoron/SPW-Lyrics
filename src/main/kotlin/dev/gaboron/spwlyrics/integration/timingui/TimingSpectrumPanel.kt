package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.TimingCursor
import dev.gaboron.spwlyrics.application.PlayingTimingWords
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.*

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingSpectrumPanel(timing: LyricsTimingController, followPlayback: Boolean, playing: PlayingTimingWords,
    focus: () -> Unit, modifier: Modifier) {
    val controller = timing.spectrum
    val state = timing.state
    val snapshot = timing.playback
    val selectedWord = state.cursor?.let { cursor -> state.lines.firstOrNull { it.id == cursor.lineId }?.words?.getOrNull(cursor.word) }
    val selectedTime = remember(selectedWord?.start) { runCatching { selectedWord?.start?.let(LyricEditTime::parse) }.getOrNull() ?: 0 }
    val center = if (followPlayback) snapshot?.positionMs ?: selectedTime else selectedTime
    val file = timing.audioPath
    var browsing by remember(file) { mutableStateOf(false) }
    LaunchedEffect(state.cursor, followPlayback) { browsing = false }
    LaunchedEffect(file, center, controller.spanMs, browsing, controller.dependency.available) {
        if (!browsing) file?.let { controller.follow(it, center, snapshot?.durationMs ?: 0) }
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val markers = remember(state.lines) {
        SpectrumWordLayout.lanes(state.lines.flatMap { line -> line.words.mapIndexedNotNull { i, word ->
            val start = runCatching { LyricEditTime.parse(word.start) }.getOrNull()
            val end = runCatching { LyricEditTime.parse(word.end) }.getOrNull()
            if (start != null && end != null && end > start && word.text.isNotBlank())
                SpectralWord(TimingCursor(line.id, i), word.text, start, end) else null
        } })
    }
    val visible = remember(markers, controller.viewStartMs, controller.spanMs) {
        markers.filter { it.start < controller.viewStartMs + controller.spanMs && it.end >= controller.viewStartMs }
    }
    RoundedColumn(modifier, paddingValues = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            JustifiedRow(startContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val zoomCenter = if (browsing) controller.viewStartMs + controller.spanMs / 2 else center
                    DesktopButton("放大", { controller.zoom(true, zoomCenter) }, enabled = controller.spanMs > 2000)
                    DesktopButton("缩小", { controller.zoom(false, zoomCenter) }, enabled = controller.spanMs < 60000)
                    DesktopButton("定位所选", { browsing = false; file?.let { controller.follow(it, selectedTime, snapshot?.durationMs ?: 0, force = true) } })
                }
            }, endContent = {
                DesktopButton(if (controller.loading) "分析中…" else "重新分析", controller::retry, enabled = !controller.loading)
            }, verticalAlignment = Alignment.CenterVertically)
            LyricsTransition(controller.data != null, Modifier.weight(1f).fillMaxWidth()) { available ->
                val data = controller.data
                if (available && data != null) {
                    TimingSpectrumImage(data, controller.viewStartMs, controller.spanMs, visible, state.cursor,
                        playing.cursors, snapshot?.positionMs, snapshot?.durationMs ?: 0, { time ->
                        val candidates = markers.filter { time >= it.start && time < it.end }
                        (candidates.firstOrNull { it.cursor == state.cursor } ?: candidates.firstOrNull())?.let { timing.choose(it.cursor) }
                        timing.seekTo(time)
                        focus()
                    }, { timing.editRange(it.cursor, it.start, it.end); focus() },
                        { browsing = true; controller.panBy(it) }, Modifier.fillMaxSize())
                } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LyricsTransition(controller.loading to controller.error) { (loading, error) ->
                        LyricsStatus(if (loading) "正在通过 Runtime 解码并分析当前时间窗…" else error ?: "打开歌曲后分析频谱。", error != null)
                    }
                }
            }
            AnimatedVisibility(controller.data != null && controller.error != null,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                LyricsTransition(controller.error) { error -> error?.let { LyricsStatus(it, true) } }
            }
            LyricsStatus("滚轮横移 · 拖动字块平移，边缘调整词首尾 · Ctrl+Z 撤销")
            TimingRuntimeNotice(controller.dependency)
        }
    }
}
