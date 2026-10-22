package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
internal fun DesktopCheck(checked: Boolean, onChange: () -> Unit, enabled: Boolean, text: String = "", modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (checked) SaltTheme.colors.highlight else Color.Transparent, tween(160), label = "check fill")
    val mark by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "check mark")
    Row(modifier.desktopControlMotion(enabled).clip(SaltTheme.shapes.medium)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox) { onChange() }.padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(fill)
            .border(1.dp, if (checked) SaltTheme.colors.highlight else SaltTheme.colors.subText, CircleShape), contentAlignment = Alignment.Center) {
            DesktopIcon(DesktopGlyph.Check, SaltTheme.colors.onHighlight,
                Modifier.size(13.dp).graphicsLayer { alpha = mark; scaleX = mark; scaleY = mark })
        }
        if (text.isNotBlank()) Text(text, color = if (enabled) SaltTheme.colors.text else SaltTheme.colors.subText)
    }
}
