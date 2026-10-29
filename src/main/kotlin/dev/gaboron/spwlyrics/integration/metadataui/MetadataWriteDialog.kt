package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopDialog
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

@Composable
internal fun MetadataWriteDialog(controller: MetadataEditorController) {
    val request = controller.state.pendingWrite ?: return
    DesktopDialog(controller::cancelWrite) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("确认歌词写入格式")
            Text(request.original.path.fileName.toString())
            MetadataFormatPicker(request.format, controller::format, enabled = !controller.state.busy)
            if (controller.state.error) LyricsStatus(controller.state.status, true)
            if (request.losses.isEmpty()) Text("此格式可保留当前歌词的信息。")
            else {
                Text("${request.format.label} 无法保留以下信息：")
                request.losses.forEach { Text("· $it") }
                Text("可继续按此格式写入，或选择其他格式。TTML 可保留这些信息。")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopButton("按 ${request.format.label} 写入", controller::confirmWrite, primary = true)
                DesktopButton("继续编辑", controller::cancelWrite)
            }
        }
    }
}
