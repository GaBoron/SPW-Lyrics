package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLyricActions(controller: MetadataEditorController) {
    val state = controller.state
    val enabled = state.canEditRows
    val selected = enabled && state.selectedLineIds.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("添加歌词", controller::addLine, enabled = enabled && !state.validating)
            DesktopButton("删除所选", controller::deleteSelected, enabled = selected && !state.validating)
            DesktopButton("全选", controller::selectAllLines, enabled = enabled && state.structured != null)
            Text("已选 ${state.selectedLineIds.size} 句 · Ctrl 点选 / Shift 连选", color = DesktopColors.Muted, fontSize = 10.sp)
        }
    }
}
