package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.domain.LyricVocalPart

@Composable
internal fun MetadataLyricActions(controller: MetadataEditorController) {
    val state = controller.state
    val enabled = state.canEditRows
    val selected = enabled && state.selectedLineIds.isNotEmpty()
    var delay by remember { mutableStateOf("0") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("添加歌词", controller::addLine, enabled = enabled && !state.validating)
            DesktopButton("删除所选", controller::deleteSelected, enabled = selected && !state.validating)
            DesktopButton("全选", controller::selectAllLines, enabled = enabled && state.structured != null)
            Text("已选 ${state.selectedLineIds.size} 句 · Ctrl 点选 / Shift 连选", color = DesktopColors.Muted, fontSize = 10.sp)
        }
        if (state.selectedLineIds.size > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("批量", color = DesktopColors.Muted, fontSize = 11.sp)
            DesktopField(delay, { delay = it }, Modifier.width(80.dp), enabled = selected)
            DesktopButton("延迟 ms", { controller.shiftSelected(delay) }, enabled = selected)
            DesktopButton("主唱（左）", { controller.editSelected { it.copy(vocalPart = LyricVocalPart.MAIN) } }, enabled = selected)
            DesktopButton("对唱（右）", { controller.editSelected { it.copy(vocalPart = LyricVocalPart.DUET) } }, enabled = selected)
            DesktopButton("背景", { controller.editSelected { it.copy(background = true) } }, enabled = selected)
            DesktopButton("取消背景", { controller.editSelected { it.copy(background = false) } }, enabled = selected)
        }
    }
}
