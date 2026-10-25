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
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataLyricLineEditor(line: EditableLyricLine, controller: MetadataEditorController, modifier: Modifier) {
    val enabled = !controller.state.busy
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().padding(end = 12.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("第 ${controller.state.lineNumbers[line.id]} 句", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            MetadataLineVoice(controller, enabled)
            MetadataLineTiming(line, controller, enabled)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("歌词正文", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(controller.state.lineText(line.id), { value -> controller.editLine { it.copy(text = value, words = emptyList()) } }, Modifier.fillMaxWidth().height(62.dp),
                    enabled = enabled, placeholder = "输入这一句歌词")
                if (line.words.isNotEmpty()) Text("替换整句正文会清除本句逐字时间；下方逐字编辑可保留时间。", fontSize = 10.sp, color = DesktopColors.Muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("翻译", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(line.translation, { value -> controller.editLine { it.copy(translation = value) } },
                    Modifier.fillMaxWidth().height(64.dp), enabled = enabled, placeholder = "添加翻译")
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("音译", fontSize = 12.sp, color = DesktopColors.Muted)
                MetadataTextArea(line.romanization, { value -> controller.editLine { it.copy(romanization = value) } },
                    Modifier.fillMaxWidth().height(64.dp), enabled = enabled, placeholder = "添加音译")
            }
            if (line.words.isNotEmpty()) MetadataWordEditor(line, controller, enabled)
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
