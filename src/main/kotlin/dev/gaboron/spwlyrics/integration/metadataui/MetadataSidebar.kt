package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph

/** Keep the same header and toggle while the field pane changes width. */
@Composable
internal fun MetadataSidebar(controller: MetadataEditorController, expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, importWidth: Dp? = null) {
    val fieldsState = rememberSaveableStateHolder()
    val importing = importWidth != null
    val shown = expanded || importing
    val width by animateDpAsState(importWidth ?: if (expanded) 250.dp else 64.dp, tween(260), label = "metadata sidebar width")
    Column(modifier.width(width).fillMaxHeight().clipToBounds(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Retain the title's allocation after its exit so the toggle keeps its trailing anchor.
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(shown,
                    enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(if (importing) "当前草稿" else "歌曲信息", color = SaltTheme.colors.subText, maxLines = 1, softWrap = false)
                }
            }
            if (!importing) DesktopButton(if (expanded) "收起" else "展开", { onExpandedChange(!expanded) },
                glyph = if (expanded) DesktopGlyph.Back else null, sizeAlignment = Alignment.CenterEnd)
        }
        AnimatedVisibility(shown, Modifier.weight(1f), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            fieldsState.SaveableStateProvider("metadata-fields") {
                MetadataFieldsPanel(controller, Modifier.fillMaxSize(), includeLyrics = importing)
            }
        }
    }
}
