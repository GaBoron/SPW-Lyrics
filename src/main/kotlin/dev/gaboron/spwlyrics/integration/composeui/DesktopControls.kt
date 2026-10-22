package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DesktopButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, loading: Boolean = false,
    glyph: DesktopGlyph? = null,
) {
    com.moriafly.salt.ui.Button(onClick, modifier = modifier.desktopControlMotion(enabled).animateContentSize(tween(160)), enabled = enabled,
        appearance = if (primary) com.moriafly.salt.ui.ButtonAppearance.Filled else com.moriafly.salt.ui.ButtonAppearance.Subtle) {
        val ink by animateColorAsState(com.moriafly.salt.ui.LocalContentColor.current, tween(150), label = "button ink")
        if (loading) { CircularProgressIndicator(Modifier.size(14.dp), color = ink, strokeWidth = 1.5.dp); Spacer(Modifier.width(7.dp)) }
        else if (glyph != null) { DesktopIcon(glyph, ink); Spacer(Modifier.width(7.dp)) }
        Crossfade(text, animationSpec = tween(140), label = "button label") { Text(it, color = ink, maxLines = 1) }
    }
}

@Composable
internal fun EmptyWorkspace(title: String, detail: String = "", loading: Boolean = false, glyph: DesktopGlyph = DesktopGlyph.Search) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = DesktopColors.Accent, strokeWidth = 2.dp)
        else DesktopIcon(glyph, DesktopColors.Muted.copy(alpha = .5f), Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = DesktopColors.Text)
        if (detail.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(detail, style = com.moriafly.salt.ui.SaltTheme.textStyles.sub, color = DesktopColors.Muted) }
    }
}
