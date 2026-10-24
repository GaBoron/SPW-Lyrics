package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopCheck

@Composable
internal fun MetadataImportPanel(controller: MetadataEditorController) {
    val state = controller.state
    MetadataContentTransition(state.onlineImport, Modifier.fillMaxWidth(),
        contentKey = { it?.let { pending -> Triple(pending.source, pending.fields, pending.coverUrl) } }) { pending ->
        if (pending != null) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("在线信息 · ${pending.source}", color = SaltTheme.colors.text)
            MetadataContentTransition(pending.notice) { notice -> notice?.let { LyricsStatus(it) } }
            LyricsMenu(if (pending.fillMissing) "仅填补空字段" else "覆盖所选字段", listOf(true, false), pending.fillMissing,
                { if (it) "仅填补空字段" else "覆盖所选字段" }, controller::importMode, enabled = !state.busy)
            pending.fields.forEach { (field, value) ->
                DesktopCheck(field in pending.selected, { controller.selectImport(field, field !in pending.selected) }, !state.busy,
                    "${field.label}：${value.replace('\n', '、')}")
            }
            MetadataContentTransition(pending.cover) { cover ->
                if (cover != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataCoverPreview(cover, "在线候选封面")
                    DesktopCheck(pending.coverSelected, controller::selectCoverImport, !state.busy, "导入此候选封面")
                }
            }
            MetadataContentTransition(pending.coverNotice) { notice -> notice?.let { LyricsStatus(it) } }
            MetadataContentTransition(pending.coverUrl != null && pending.cover == null) { retry ->
                if (retry) DesktopButton("重试封面", controller::retryCoverImport, enabled = !state.busy)
            }
            DesktopButton("将所选字段应用到草稿", controller::applyImport,
                enabled = !state.busy && (pending.selected.isNotEmpty() || pending.coverSelected && pending.cover != null))
        }
    }
}
