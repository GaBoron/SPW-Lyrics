package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun ManualDelayEditor(state: ManualSearchState, controller: ManualSearchController) {
    Column(Modifier.fillMaxWidth().desktopPanel().padding(16.dp, 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("歌词延迟", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("已保存 ${state.savedDelay} ms", fontSize = 11.sp, color = DesktopColors.Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DesktopButton("−100 ms", { controller.stepDelay(-100) }, enabled = state.canMutate)
            DesktopField(state.delayText, controller::delayText, Modifier.weight(1f), enabled = state.canMutate,
                suffix = "ms", invalid = state.delayValue == null, onEnter = { controller.saveDelay() })
            DesktopButton("+100 ms", { controller.stepDelay(100) }, enabled = state.canMutate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DesktopButton("保存延迟", { controller.saveDelay() }, enabled = state.canMutate && state.delayValue != null)
            DesktopButton("恢复 0 ms", { controller.saveDelay(reset = true) }, enabled = state.canMutate)
        }
        Text("仅应用于当前歌曲；正数延后，负数提前", fontSize = 10.sp, color = DesktopColors.Muted)
    }
}
