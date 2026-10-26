package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLineVoice(controller: MetadataEditorController, enabled: Boolean) {
    val state = controller.state
    val selected = state.selectedLineIds.mapNotNull(state::line)
    val allBackground = selected.isNotEmpty() && selected.all { it.background }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (selected.size > 1) "演唱声部 · 应用于所选 ${selected.size} 句" else "演唱声部",
            color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("主唱（左）", { controller.editSelected { it.copy(vocalPart = LyricVocalPart.MAIN) } },
                enabled = enabled, primary = selected.isNotEmpty() && selected.all { state.vocalPart(it) == LyricVocalPart.MAIN })
            DesktopButton("对唱（右）", { controller.editSelected { it.copy(vocalPart = LyricVocalPart.DUET) } },
                enabled = enabled, primary = selected.isNotEmpty() && selected.all { state.vocalPart(it) == LyricVocalPart.DUET })
            DesktopCheck(allBackground, { controller.editSelected { it.copy(background = !allBackground) } }, enabled, "背景人声")
        }
    }
}

@Composable
internal fun MetadataLineTiming(line: EditableLyricLine, controller: MetadataEditorController, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LyricEditField("开始时间", line.start, { value -> controller.editLine { it.copy(start = value) } }, enabled, Modifier.weight(1f))
            LyricEditField("结束时间", line.end, { value -> controller.editLine { it.copy(end = value) } }, enabled, Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("整句平移", color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopButton("−100", { controller.shiftLine(-100) }, Modifier.height(40.dp), enabled = enabled)
                    DesktopButton("+100", { controller.shiftLine(100) }, Modifier.height(40.dp), enabled = enabled)
                }
            }
        }
        Text("时间格式 mm:ss.mmm，或整数毫秒；平移单位 ms，同时调整整句和逐字时间。",
            color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
    }
}

@Composable
internal fun LyricEditField(label: String, value: String, change: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
        DesktopField(value, change, Modifier.fillMaxWidth(), enabled = enabled)
    }
}
