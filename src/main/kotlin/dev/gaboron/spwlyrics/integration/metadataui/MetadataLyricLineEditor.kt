package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLyricLineEditor(line: EditableLyricLine, controller: MetadataEditorController, modifier: Modifier) {
    val enabled = !controller.state.busy
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().padding(end = 12.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("第 ${line.id + 1} 句", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                DesktopCheck(line.background, { controller.editLine { it.copy(background = !it.background) } }, enabled, "背景人声")
                DesktopButton("−100 ms", { controller.shiftLine(-100) }, enabled = enabled)
                DesktopButton("+100 ms", { controller.shiftLine(100) }, enabled = enabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LyricEditField("开始时间", line.start, { value -> controller.editLine { it.copy(start = value) } }, enabled, Modifier.weight(1f))
                LyricEditField("结束时间", line.end, { value -> controller.editLine { it.copy(end = value) } }, enabled, Modifier.weight(1f))
            }
            Text("格式 mm:ss.mmm，也可填写毫秒数；±100 ms 同时平移整句和逐字时间。",
                fontSize = 10.sp, lineHeight = 16.sp, color = DesktopColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("演唱声部", fontSize = 12.sp, color = DesktopColors.Muted)
                DesktopButton("主唱（左）", { controller.editLine { it.copy(vocalPart = LyricVocalPart.MAIN) } },
                    enabled = enabled, primary = controller.state.vocalPart(line) == LyricVocalPart.MAIN)
                DesktopButton("对唱（右）", { controller.editLine { it.copy(vocalPart = LyricVocalPart.DUET) } },
                    enabled = enabled, primary = controller.state.vocalPart(line) == LyricVocalPart.DUET)
            }
            Text("主唱靠左，对唱靠右；背景人声单独设置。",
                fontSize = 10.sp, lineHeight = 16.sp, color = DesktopColors.Muted)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("歌词正文", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(controller.state.lineText(line.id), { value -> controller.editLine { it.copy(text = value, words = emptyList()) } }, Modifier.fillMaxWidth().height(62.dp),
                    enabled = enabled)
                if (line.words.isNotEmpty()) Text("替换整句正文会清除本句逐字时间；下方逐字编辑可保留时间。", fontSize = 10.sp, color = DesktopColors.Muted)
            }
            if (line.words.isNotEmpty()) MetadataWordEditor(line, controller, enabled)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("翻译", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(line.translation, { value -> controller.editLine { it.copy(translation = value) } },
                    Modifier.fillMaxWidth().height(58.dp), enabled = enabled)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("音译", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(line.romanization, { value -> controller.editLine { it.copy(romanization = value) } },
                    Modifier.fillMaxWidth().height(58.dp), enabled = enabled)
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
internal fun LyricEditField(label: String, value: String, change: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 11.sp, color = DesktopColors.Muted)
        DesktopField(value, change, Modifier.fillMaxWidth(), enabled = enabled)
    }
}
