package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph

/** Collapsing hides the fields without changing the controller or its unsaved draft. */
@Composable
internal fun MetadataSidebar(
    controller: MetadataEditorController,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(if (expanded) 250.dp else 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("歌曲信息", Modifier.weight(1f), color = SaltTheme.colors.subText)
                DesktopButton("收起", { onExpandedChange(false) }, glyph = DesktopGlyph.Back)
            }
            MetadataFieldsPanel(controller, Modifier.weight(1f).fillMaxWidth())
        } else {
            DesktopButton("展开", { onExpandedChange(true) }, Modifier.fillMaxWidth())
        }
    }
}
