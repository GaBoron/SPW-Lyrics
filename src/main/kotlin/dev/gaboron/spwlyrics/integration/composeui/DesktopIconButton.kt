package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DesktopIconButton(label: String, glyph: DesktopGlyph, click: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    TooltipArea(tooltip = {
        Box(Modifier.background(SaltTheme.colors.popup, SaltTheme.shapes.medium).padding(8.dp)) {
            Text(label, style = SaltTheme.textStyles.sub)
        }
    }) {
        Box(modifier.size(36.dp).desktopControlMotion(enabled).clip(SaltTheme.shapes.medium)
            .semantics { contentDescription = label }.clickable(enabled, role = Role.Button, onClick = click),
            contentAlignment = Alignment.Center) {
            DesktopIcon(glyph, SaltTheme.colors.text.copy(alpha = if (enabled) 1f else .4f), Modifier.size(18.dp))
        }
    }
}
