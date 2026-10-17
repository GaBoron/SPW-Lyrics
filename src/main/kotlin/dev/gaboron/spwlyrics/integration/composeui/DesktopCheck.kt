package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DesktopCheck(checked: Boolean, onChange: () -> Unit, enabled: Boolean, text: String = "", modifier: Modifier = Modifier) {
    Row(modifier.height(DesktopColors.ControlHeight).alpha(if (enabled) 1f else .55f).clickable(enabled = enabled, role = Role.Checkbox, onClick = onChange)
        .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).background(if (checked) DesktopColors.Accent else DesktopColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, if (checked) DesktopColors.Accent else DesktopColors.Border, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            if (checked) DesktopIcon(DesktopGlyph.Check, androidx.compose.ui.graphics.Color.White, Modifier.size(12.dp))
        }
        if (text.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text(text, fontSize = 12.sp, color = if (enabled) DesktopColors.Text else DesktopColors.Muted) }
    }
}
