package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.TimingMark
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TimingPanel(controller: LyricsTimingController) {
    val state = controller.state
    val focus = remember { FocusRequester() }
    val keyboard = remember(controller) { TimingKeyboard(controller) }
    LaunchedEffect(controller) { while (isActive) { controller.poll(); delay(40) } }
    DisposableEffect(controller) { onDispose { keyboard.clear(); controller.cancelPress() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TimingTransport(controller)
        TimingSettings(controller)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimingMarkButton("F · 词首", TimingMark.START, controller)
            TimingMarkButton("G · 连续轴", TimingMark.CONNECT, controller)
            TimingMarkButton("H · 词尾", TimingMark.END, controller)
            DesktopButton("启用键盘打轴", { focus.requestFocus() })
        }
        val selected = state.cursor?.let { cursor -> state.lines.firstOrNull { it.id == cursor.lineId }?.words?.getOrNull(cursor.word) }
        LyricsTransition(selected?.let { Triple(it.text, it.start, it.end) }) { word ->
            Text(word?.let { "当前词：${it.first}  ·  ${it.second} → ${it.third}" } ?: "点击歌词中的词开始打轴；尚未分词时先准备逐字。")
        }
        LyricsTransition(state.message to state.error) { (message, error) -> LyricsStatus(message, error) }
        TimingWordGrid(controller, { focus.requestFocus() }, Modifier.weight(1f).fillMaxWidth()
            .focusRequester(focus).onFocusChanged { if (!it.hasFocus) keyboard.clear() }
            .onPreviewKeyEvent(keyboard::event).focusable())
        LyricsStatus("歌词区快捷键：F/G/H 记录 · A/D 上下词 · W/S 上下句 · R/Y 定位上下词 · Home/End 定位句首尾 · 空格播放/暂停 · ←/→ ±1 秒 · [/] 调速 · ' 恢复 1×。输入框内不接管按键。")
    }
}

@Composable
private fun TimingMarkButton(text: String, mark: TimingMark, controller: LyricsTimingController) {
    val enabled = controller.state.canRecord
    DesktopButton(text, { controller.click(mark) }, Modifier.pointerInput(controller, mark, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            controller.press(mark)
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) == null) controller.cancelPress()
        }
    }, enabled = enabled, primary = mark == TimingMark.CONNECT)
}
