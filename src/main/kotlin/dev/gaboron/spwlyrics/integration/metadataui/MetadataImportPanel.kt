package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopCheck
import dev.gaboron.spwlyrics.domain.OnlineMetadataImport

@Composable
internal fun MetadataImportPanel(controller: MetadataEditorController, pending: OnlineMetadataImport, modifier: Modifier) {
    val state = controller.state
    val enabled = !state.busy && state.onlineImport != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("在线元数据 · ${pending.source}", color = SaltTheme.colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("仅补充", { controller.selectImportPreset(true) }, enabled = enabled)
            DesktopButton("全替换", { controller.selectImportPreset(false) }, enabled = enabled)
        }
        LyricsStatus("勾选即替换，未勾选即保留；在线空值不参与替换。")
        LyricsScroll(Modifier.weight(1f).fillMaxWidth()) {
            pending.notice?.let { LyricsStatus(it) }
            pending.fields.filterValues(String::isNotBlank).forEach { (field, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DesktopCheck(field in pending.selected, { controller.selectImport(field, field !in pending.selected) }, enabled, field.label)
                    Text(value, Modifier.padding(start = 28.dp), color = SaltTheme.colors.text)
                }
            }
            if (pending.fields.values.none(String::isNotBlank)) LyricsStatus("此来源没有可导入的歌曲标签。")
            if (pending.cover != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopCheck(pending.coverSelected, controller::selectCoverImport, enabled, "封面")
                MetadataCoverPreview(pending.cover, "在线候选封面")
            }
            pending.coverNotice?.let { LyricsStatus(it) }
            if (pending.coverUrl != null && pending.cover == null) {
                DesktopButton("重试封面", controller::retryCoverImport, enabled = enabled)
            }
            if (!pending.lyricsText.isNullOrBlank()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopCheck(pending.lyricsSelected, controller::selectLyricsImport, enabled,
                    "内嵌歌词（${pending.lyricsDocument?.lines?.size ?: 0} 行）")
                val document = pending.lyricsDocument
                if (document != null) LyricsDocumentPreview(document.lines, pending.lyricsMainAgent, state.importId,
                    Modifier.fillMaxWidth().height(320.dp))
                else Text(pending.lyricsText)
            } else {
                LyricsStatus("此候选没有可导入的歌词，可更换歌词来源后重试。")
            }
        }
    }
}
