package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.InputLatencyCalibration
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsLayoutTransition

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingInputLatencySettings(controller: LyricsTimingController, openCalibration: () -> Unit) {
    val calibration = controller.calibration
    var value by remember(calibration.saved) { mutableStateOf((calibration.saved?.delayMs ?: 0).toString()) }
    RoundedColumn(paddingValues = PaddingValues(0.dp)) {
        ItemButton(openCalibration, "鼓点输入延迟校准")
        ItemTip("播放鼓点后按空格或 G，测量输入偏差；保存后下次自动使用。正值会将打轴时间提前，并按宿主倍速换算。")
        ItemContainer {
            JustifiedRow(startContent = { Text("输入延迟") }, endContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopField(value, { if (it.length <= 4 && it.matches(Regex("-?\\d*"))) value = it }, Modifier.width(115.dp),
                        enabled = !calibration.saving, suffix = "ms", invalid = value.toIntOrNull()?.let { it !in -500..500 } ?: true)
                    DesktopButton("保存补偿", { value.toIntOrNull()?.let { calibration.save(InputLatencyCalibration(it, 0, 0.0, "手动设置")) } },
                        enabled = !calibration.saving && value.toIntOrNull()?.let { it in -500..500 } == true)
                }
            }, verticalAlignment = Alignment.CenterVertically)
        }
        LyricsLayoutTransition(calibration.saved) { saved ->
            ItemTip(saved?.let { "已保存 ${it.delayMs} ms · ${it.device}。更换输出设备后可重新校准。" } ?: "尚未保存输入校准，当前按 0 ms 记录。")
        }
        AnimatedVisibility(calibration.error, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LyricsLayoutTransition(calibration.message) { ItemTip(it) }
        }
    }
}
