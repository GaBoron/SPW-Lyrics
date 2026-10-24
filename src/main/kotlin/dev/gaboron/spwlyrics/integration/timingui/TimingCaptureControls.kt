package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.popup.*
import dev.gaboron.spwlyrics.domain.TimingMark
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingCaptureControls(controller: LyricsTimingController, keyboard: Boolean, focus: () -> Unit,
    settings: Boolean, toggleSettings: () -> Unit, follow: Boolean, toggleFollow: () -> Unit) {
    val state = controller.state
    var expanded by remember { mutableStateOf(false) }
    ItemContainer {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val capture: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimingMarkButton("F 词首", TimingMark.START, controller)
                    TimingMarkButton("G 连续轴", TimingMark.CONNECT, controller)
                    TimingMarkButton("H 词尾", TimingMark.END, controller)
                    Spacer(Modifier.width(8.dp))
                    DesktopButton(if (keyboard) "键盘已启用" else "启用键盘", focus, primary = keyboard)
                }
            }
            val options: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DesktopButton(if (follow) "跟随播放" else "跟随所选", toggleFollow, primary = !follow)
                    Box {
                        DesktopButton("更多", { expanded = true })
                        PopupMenu(expanded, { expanded = false }) {
                            ItemButton({ controller.undo(); expanded = false }, "撤销  Ctrl+Z", enabled = state.canUndo && !state.busy)
                            ItemButton({ controller.undo(true); expanded = false }, "重做  Ctrl+Y", enabled = state.canRedo && !state.busy)
                            ItemButton({ controller.seekToCursor(); expanded = false }, "定位所选词", enabled = state.available && state.cursor != null && !state.busy)
                            ItemButton({ controller.skipLine(); expanded = false }, "跳过本句", enabled = state.cursor != null && !state.busy)
                            ItemButton({ controller.rate(1f); expanded = false }, "恢复 1×", enabled = state.available && !state.busy)
                        }
                    }
                    DesktopButton(if (settings) "收起设置" else "打轴设置", toggleSettings, primary = settings)
                }
            }
            dev.gaboron.spwlyrics.integration.workspace.LyricsLayoutTransition(maxWidth >= 680.dp) { wide ->
                if (wide) JustifiedRow(startContent = capture, endContent = options, verticalAlignment = Alignment.CenterVertically)
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capture()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { options() }
                }
            }
        }
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
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) == null) controller.cancelPress(mark)
        }
    }, enabled = enabled, primary = mark == TimingMark.CONNECT)
}
