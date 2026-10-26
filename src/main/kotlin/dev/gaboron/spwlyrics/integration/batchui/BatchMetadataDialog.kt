package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.DesktopDialog
import dev.gaboron.spwlyrics.application.BatchMetadataOptions
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopCheck
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

@Composable
internal fun BatchMetadataDialog(count: Int, dismiss: () -> Unit, start: (BatchMetadataOptions) -> Unit) {
    var options by remember { mutableStateOf(BatchMetadataOptions()) }
    DesktopDialog(dismiss) {
        Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("向 $count 首歌曲写入哪些数据？")
            LyricsMenu(if (options.fillMissing) "仅补充空数据" else "覆盖所选数据", listOf(true, false), options.fillMissing,
                { if (it) "仅补充空数据" else "覆盖所选数据" }, { options = options.copy(fillMissing = it) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AudioMetadataField.entries.forEach { field ->
                    DesktopCheck(field in options.fields, {
                        options = options.copy(fields = if (field in options.fields) options.fields - field else options.fields + field)
                    }, true, field.label)
                }
                DesktopCheck(options.cover, { options = options.copy(cover = !options.cover) }, true, "封面")
                DesktopCheck(options.lyrics, { options = options.copy(lyrics = !options.lyrics) }, true, "内嵌歌词（TTML）")
            }
            LyricsStatus(if (options.fillMissing) "只补充歌曲中为空的数据，保留已有内容。来源未提供的字段不会清空。"
                else "仅覆盖勾选的数据，其他标签保留。来源未提供的字段不会清空。")
            LyricsStatus("自动匹配不可靠的歌曲会跳过；保留已有的单曲手动选择。写入后需重新扫描曲库。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                DesktopButton("取消", dismiss)
                DesktopButton("开始写入", { start(options) }, enabled = options.hasSelection, primary = true)
            }
        }
    }
}
