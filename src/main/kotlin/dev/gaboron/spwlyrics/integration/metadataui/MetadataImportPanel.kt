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
    val pending = state.onlineImport ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("在线信息 · ${pending.source}", color = SaltTheme.colors.text)
        pending.notice?.let { LyricsStatus(it) }
        LyricsMenu(if (pending.fillMissing) "仅填补空字段" else "覆盖所选字段", listOf(true, false), pending.fillMissing,
            { if (it) "仅填补空字段" else "覆盖所选字段" }, controller::importMode, enabled = !state.busy)
        pending.fields.forEach { (field, value) ->
            DesktopCheck(field in pending.selected, { controller.selectImport(field, field !in pending.selected) }, !state.busy,
                "${field.label}：${value.replace('\n', '、')}")
        }
        pending.cover?.let { cover ->
            MetadataCoverPreview(cover, "在线候选封面")
            DesktopCheck(pending.coverSelected, controller::selectCoverImport, !state.busy, "导入此候选封面")
        }
        pending.coverNotice?.let { LyricsStatus(it) }
        if (pending.coverUrl != null && pending.cover == null) {
            DesktopButton("重试封面", controller::retryCoverImport, enabled = !state.busy)
        }
        DesktopButton("将所选字段应用到草稿", controller::applyImport,
            enabled = !state.busy && (pending.selected.isNotEmpty() || pending.coverSelected && pending.cover != null))
    }
}
