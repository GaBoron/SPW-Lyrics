package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DesktopButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, loading: Boolean = false,
    glyph: DesktopGlyph? = null,
) {
    com.moriafly.salt.ui.Button(onClick, modifier = modifier, enabled = enabled,
        appearance = if (primary) com.moriafly.salt.ui.ButtonAppearance.Filled else com.moriafly.salt.ui.ButtonAppearance.Subtle) {
        val ink = com.moriafly.salt.ui.LocalContentColor.current
        if (loading) { CircularProgressIndicator(Modifier.size(14.dp), color = ink, strokeWidth = 1.5.dp); Spacer(Modifier.width(7.dp)) }
        else if (glyph != null) { DesktopIcon(glyph, ink); Spacer(Modifier.width(7.dp)) }
        Text(text, maxLines = 1)
    }
}

@Composable
internal fun DesktopField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", enabled: Boolean = true, onEnter: () -> Unit = {},
    glyph: DesktopGlyph? = null, suffix: String? = null, invalid: Boolean = false,
) {
    Row(modifier.clip(com.moriafly.salt.ui.SaltTheme.shapes.medium)
        .background(DesktopColors.Surface)
        .then(if (invalid) Modifier.border(1.dp, DesktopColors.Error, com.moriafly.salt.ui.SaltTheme.shapes.medium) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) DesktopIcon(glyph, DesktopColors.Muted, Modifier.padding(start = 10.dp).size(15.dp))
        @OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
        com.moriafly.salt.ui.ItemEdit(value, onValueChange, Modifier.weight(1f)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isCtrlPressed) {
                onEnter(); true
            } else false
        }, hint = placeholder, readOnly = !enabled)
        if (suffix != null) Text(suffix, Modifier.padding(end = 10.dp), color = DesktopColors.Muted, fontSize = 11.sp)
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
