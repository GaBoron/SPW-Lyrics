package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.TimingPlaybackIndex
import dev.gaboron.spwlyrics.integration.workspace.*
import kotlinx.coroutines.isActive

private enum class TimingToolView { LYRICS, SETTINGS, CALIBRATION }

@Composable
internal fun TimingPanel(controller: LyricsTimingController) {
    val state = controller.state
    val focus = remember { FocusRequester() }
    val keyboard = remember(controller) { TimingKeyboard(controller) }
    var keyboardActive by remember { mutableStateOf(false) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(TimingToolView.LYRICS) }
    var spectrum by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(controller.selectionRevision) { follow = false }
    // Wait until pointer/button focus and navigation composition settle before enabling shortcuts.
    LaunchedEffect(tool, focusRequest) {
        if (tool == TimingToolView.LYRICS) { withFrameNanos { }; keyboard.clear(); focus.requestFocus() }
    }
    val index = remember(state.lines) { TimingPlaybackIndex(state.lines) }
    val playing = controller.playback?.let { index.at(it.positionMs) } ?: dev.gaboron.spwlyrics.application.PlayingTimingWords()
    LaunchedEffect(controller) { while (isActive) { controller.poll(); controller.calibration.poll(); withFrameNanos { } } }
    DisposableEffect(controller) { onDispose { keyboard.clear(); controller.leave(); controller.spectrum.cancel(); controller.calibration.stop() } }
    ProvideControlSize(ControlSize.Small) {
        Column(Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && tool != TimingToolView.LYRICS) {
                controller.calibration.stop(); tool = TimingToolView.LYRICS; true
            } else if (keyboardActive && tool == TimingToolView.LYRICS) keyboard.event(it) else false
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(tool == TimingToolView.LYRICS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                    TimingTransport(controller)
                    ItemDivider()
                    TimingCaptureControls(controller, keyboardActive, { focusRequest++ },
                        { tool = TimingToolView.SETTINGS },
                        follow, { follow = !follow }, spectrum, { spectrum = !spectrum })
                }
            }
            val selected = state.cursor?.let { cursor -> state.lines.firstOrNull { it.id == cursor.lineId }?.words?.getOrNull(cursor.word) }
            AnimatedVisibility(tool == TimingToolView.LYRICS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                LyricsLayoutTransition(selected?.text to (state.cursor?.lineId to state.error), Modifier.fillMaxWidth()) { (word, _) ->
                    LyricsStatus(if (state.error) state.message else word?.let { "所选词：$it　　选中为主高亮，播放为次高亮（支持重叠）" }
                        ?: "点击歌词选择打轴位置", state.error)
                }
            }
            LyricsPageTransition(tool, { it.ordinal }, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    TimingToolView.SETTINGS -> TimingSettingsPage(controller,
                        { tool = TimingToolView.CALIBRATION }, { tool = TimingToolView.LYRICS })
                    TimingToolView.CALIBRATION -> TimingCalibrationPanel(controller) { controller.calibration.stop(); tool = TimingToolView.SETTINGS }
                    TimingToolView.LYRICS -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimingWordGrid(controller, playing, follow, { focusRequest++ }, Modifier.weight(1f).fillMaxWidth()
                            .focusRequester(focus).onFocusChanged { keyboardActive = it.hasFocus; if (!it.hasFocus) keyboard.clear() }
                            .focusable(), list)
                        AnimatedVisibility(spectrum, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            TimingSpectrumPanel(controller, follow, playing, { focus.requestFocus() }, Modifier.fillMaxWidth().height(240.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(tool == TimingToolView.LYRICS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                LyricsStatus(if (state.error) state.message else if (!state.available) state.message
                    else "F 词首　G 连续轴　H 词尾　|　空格播放 / 暂停　|　其余操作与快捷键见打轴设置", state.error)
            }
        }
    }
}
