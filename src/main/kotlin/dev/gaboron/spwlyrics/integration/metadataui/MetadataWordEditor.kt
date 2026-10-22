package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataWordEditor(line: EditableLyricLine, controller: MetadataEditorController, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("逐字时间与文本", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("开始", fontSize = 10.sp, color = DesktopColors.Muted, modifier = Modifier.width(98.dp))
            Text("结束", fontSize = 10.sp, color = DesktopColors.Muted, modifier = Modifier.width(98.dp))
            Text("字 / 词", fontSize = 10.sp, color = DesktopColors.Muted)
        }
        line.words.forEachIndexed { index, word ->
            fun update(change: (dev.gaboron.spwlyrics.domain.EditableLyricWord) -> dev.gaboron.spwlyrics.domain.EditableLyricWord) {
                controller.editLine { current -> current.copy(words = current.words.mapIndexed { i, old -> if (i == index) change(old) else old }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopField(word.start, { update { old -> old.copy(start = it) } }, Modifier.width(98.dp), enabled = enabled)
                DesktopField(word.end, { update { old -> old.copy(end = it) } }, Modifier.width(98.dp), enabled = enabled)
                DesktopField(word.text, { update { old -> old.copy(text = it) } }, Modifier.weight(1f), enabled = enabled && !word.hasMarkup)
            }
        }
        if (line.words.any { it.hasMarkup }) Text("含注音等扩展的单词可在此改时间；其文本请在“编辑原文”中修改，以保留扩展信息。",
            fontSize = 10.sp, lineHeight = 16.sp, color = DesktopColors.Muted)
    }
}
