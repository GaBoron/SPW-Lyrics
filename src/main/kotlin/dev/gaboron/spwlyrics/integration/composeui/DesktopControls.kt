package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.CircularProgressIndicator
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Modifier.desktopPanel() = clip(RoundedCornerShape(12.dp))
    .background(DesktopColors.Surface).border(1.dp, DesktopColors.Border, RoundedCornerShape(12.dp))

@Composable
internal fun DesktopButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, loading: Boolean = false,
    glyph: DesktopGlyph? = null,
) {
    val ink = if (primary) com.moriafly.salt.ui.SaltTheme.colors.onHighlight else DesktopColors.Text
    com.moriafly.salt.ui.Button(onClick, modifier = modifier, enabled = enabled,
        appearance = if (primary) com.moriafly.salt.ui.ButtonAppearance.Filled else com.moriafly.salt.ui.ButtonAppearance.Subtle) {
        if (loading) { CircularProgressIndicator(Modifier.size(14.dp), color = ink, strokeWidth = 1.5.dp); Spacer(Modifier.width(7.dp)) }
        else if (glyph != null) { DesktopIcon(glyph, ink); Spacer(Modifier.width(7.dp)) }
        Text(text, color = ink, fontSize = 12.sp, fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
internal fun DesktopField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", enabled: Boolean = true, onEnter: () -> Unit = {},
    glyph: DesktopGlyph? = null, suffix: String? = null, invalid: Boolean = false,
) {
    Row(modifier.background(DesktopColors.Surface, RoundedCornerShape(7.dp))
        .border(1.dp, if (invalid) DesktopColors.Error else DesktopColors.Border, RoundedCornerShape(7.dp)),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ElidedText(text: String, modifier: Modifier = Modifier, color: Color = DesktopColors.Text, size: Int = 12) {
    TooltipArea(tooltip = {
        Box(Modifier.widthIn(max = 360.dp).desktopPanel().padding(10.dp)) { Text(text, fontSize = 12.sp) }
    }, modifier = modifier, delayMillis = 650) {
        Text(text.ifBlank { "—" }, color = color, fontSize = size.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun QuietTag(text: String, accent: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (accent) DesktopColors.Selected else DesktopColors.Hover)
        .padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, color = if (accent) DesktopColors.Accent else DesktopColors.Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
internal fun DesktopDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopColors.Border.copy(alpha = .65f)))

@Composable
internal fun EmptyWorkspace(title: String, detail: String = "", loading: Boolean = false, glyph: DesktopGlyph = DesktopGlyph.Search) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = DesktopColors.Accent, strokeWidth = 2.dp)
        else DesktopIcon(glyph, DesktopColors.Muted.copy(alpha = .5f), Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 13.sp, color = DesktopColors.Muted)
        if (detail.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(detail, fontSize = 11.sp, color = DesktopColors.Muted) }
    }
}
