package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.TimingLatencyCalibration
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.*
import java.util.Locale
import kotlin.math.*

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingCalibrationPanel(timing: LyricsTimingController, close: () -> Unit) {
    val controller = timing.calibration
    val focus = remember { FocusRequester() }
    val held = remember { mutableSetOf<Key>() }
    LaunchedEffect(controller, controller.running, controller.saving) { focus.requestFocus() }
    DisposableEffect(controller) { onDispose { held.clear(); controller.stop() } }
    Column(Modifier.fillMaxSize().focusRequester(focus).onFocusChanged {
        if (!it.hasFocus) { held.clear(); controller.cancelPress() }
    }.onPreviewKeyEvent {
        if (it.key !in setOf(Key.Spacebar, Key.G) || it.isCtrlPressed || it.isAltPressed || it.isMetaPressed) false
        else when (it.type) {
            KeyEventType.KeyDown -> { if (held.add(it.key)) controller.press(); true }
            KeyEventType.KeyUp -> { if (held.remove(it.key)) controller.release(); true }
            else -> false
        }
    }.focusable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            ItemContainer {
                JustifiedRow(startContent = { Text("鼓点输入延迟校准") }, endContent = { DesktopButton("返回设置", close) },
                    verticalAlignment = Alignment.CenterVertically)
            }
            ItemTip("先听 4 拍，再跟随鼓点按空格或 G；每拍只计一次。开始时会暂停宿主，关闭面板即停止鼓点。")
            ItemContainer {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsMenu("${controller.bpm} BPM", listOf(80, 100, 120, 140, 160), controller.bpm,
                        { "$it BPM" }, controller::bpm, enabled = !controller.running && !controller.saving)
                    DesktopButton(if (controller.running) "重新开始" else "开始校准", { timing.startCalibration(); focus.requestFocus() }, enabled = !controller.saving)
                    DesktopButton("停止鼓点", controller::stop, enabled = controller.running)
                    DesktopButton("保存本次结果", { controller.save() }, enabled = controller.candidate != null && !controller.saving, primary = true)
                }
            }
        }
        val phase = ((controller.positionMs - TimingLatencyCalibration.LEAD_MS) / (60000.0 / controller.bpm))
        val beat = floor(phase).toInt()
        val pulse by animateColorAsState(if (controller.running && phase >= 0 && phase - floor(phase) < .2)
            SaltTheme.colors.highlight else SaltTheme.colors.subText.copy(alpha = .25f), tween(90), label = "calibration beat")
        RoundedColumn(Modifier.weight(1f), paddingValues = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(62.dp)) { drawCircle(pulse) }
                Spacer(Modifier.height(14.dp))
                LyricsTransition(when {
                    !controller.running -> "准备就绪"
                    beat < 4 -> "预备 ${((beat + 1).coerceAtLeast(0))} / 4"
                    else -> "有效敲击 ${controller.count} / 16"
                }) { Text(it) }
                Spacer(Modifier.height(16.dp))
                DesktopButton("跟随鼓点敲击  Space / G", controller::click,
                    Modifier.fillMaxWidth().height(66.dp).pointerInput(controller, controller.running) {
                        if (controller.running) awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            controller.press()
                            if (waitForUpOrCancellation(PointerEventPass.Initial) == null) controller.cancelPress()
                        }
                    }, enabled = controller.running, primary = true)
                Spacer(Modifier.height(12.dp))
                LyricsTransition(controller.lastOffset?.roundToInt()) { value ->
                    LyricsStatus(value?.let { "最近一次：${if (it >= 0) "+" else ""}$it ms" } ?: "等待敲击")
                }
                LyricsLayoutTransition(controller.candidate) { result ->
                    if (result != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("建议输入延迟 ${result.delayMs} ms · 采用 ${result.used} 次")
                        LyricsStatus(String.format(Locale.ROOT, "敲击波动 %.1f ms", result.spreadMs))
                    }
                }
            }
        }
        LyricsLayoutTransition(controller.message to controller.error) { (message, error) -> LyricsStatus(message, error) }
    }
}
