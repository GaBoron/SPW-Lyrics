package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsSection

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun ManualDelayEditor(state: ManualSearchState, controller: ManualSearchController) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LyricsSection {
        Item(onClick = { expanded = !expanded }, text = "时间调整", sub = "已保存 ${state.savedDelay} ms")
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DesktopButton("−100", { controller.stepDelay(-100) }, enabled = state.canMutate)
                DesktopField(state.delayText, controller::delayText, Modifier.weight(1f), enabled = state.canMutate,
                    suffix = "ms", invalid = state.delayValue == null, onEnter = { controller.saveDelay() })
                DesktopButton("+100", { controller.stepDelay(100) }, enabled = state.canMutate)
            }
            FlowRow(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopButton("保存", { controller.saveDelay() }, enabled = state.canMutate && state.delayValue != null, primary = true)
                DesktopButton("重置", { controller.saveDelay(reset = true) }, enabled = state.canMutate)
            }
            ItemTip("正数延后，负数提前；只调整当前歌曲。")
        }
    }
}
