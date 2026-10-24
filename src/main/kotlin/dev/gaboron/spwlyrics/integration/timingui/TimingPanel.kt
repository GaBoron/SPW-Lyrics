package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.TimingPlaybackIndex
import dev.gaboron.spwlyrics.integration.workspace.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TimingPanel(controller: LyricsTimingController) {
    val state = controller.state
    val focus = remember { FocusRequester() }
    val keyboard = remember(controller) { TimingKeyboard(controller) }
    var keyboardActive by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }
    val index = remember(state.lines) { TimingPlaybackIndex(state.lines) }
    val playing = controller.playback?.let { index.at(it.positionMs) } ?: dev.gaboron.spwlyrics.application.PlayingTimingWords()
    LaunchedEffect(controller) { controller.enter(); while (isActive) { controller.poll(); delay(40) } }
    DisposableEffect(controller) { onDispose { keyboard.clear(); controller.cancelPress() } }
    ProvideControlSize(ControlSize.Small) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                TimingTransport(controller)
                ItemDivider()
                TimingCaptureControls(controller, keyboardActive, { focus.requestFocus() }, settings, { settings = !settings }, follow, { follow = !follow })
            }
            AnimatedVisibility(settings, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                TimingSettings(controller)
            }
            val selected = state.cursor?.let { cursor -> state.lines.firstOrNull { it.id == cursor.lineId }?.words?.getOrNull(cursor.word) }
            LyricsLayoutTransition(selected?.text to (state.cursor?.lineId to state.error), Modifier.fillMaxWidth()) { (word, _) ->
                LyricsStatus(if (state.error) state.message else word?.let { "所选词：$it　　描边为编辑位置，底色为当前播放位置" }
                    ?: "点击歌词选择打轴位置", state.error)
            }
            TimingWordGrid(controller, playing, follow, { focus.requestFocus() }, Modifier.weight(1f).fillMaxWidth()
                .focusRequester(focus).onFocusChanged { keyboardActive = it.hasFocus; if (!it.hasFocus) keyboard.clear() }
                .onPreviewKeyEvent(keyboard::event).focusable())
            LyricsStatus(if (state.error) state.message else if (!state.available) state.message
                else "F 词首　G 连续轴　H 词尾　|　空格播放 / 暂停　|　其余操作与快捷键见打轴设置", state.error)
        }
    }
}
