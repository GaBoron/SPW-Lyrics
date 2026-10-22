package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** One Salt surface per input, with the same visible frame for empty and populated fields. */
@Composable
private fun Modifier.inputSurface(invalid: Boolean): Modifier {
    var focused by remember { mutableStateOf(false) }
    val stroke by animateColorAsState(when { invalid -> DesktopColors.Error; focused -> DesktopColors.Accent
        else -> SaltTheme.colors.stroke }, tween(150), label = "input focus")
    return onFocusChanged { focused = it.hasFocus }.clip(SaltTheme.shapes.medium)
        .background(DesktopColors.Surface).border(1.dp, stroke, SaltTheme.shapes.medium)
}

@Composable
internal fun DesktopField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", enabled: Boolean = true, onEnter: () -> Unit = {},
    glyph: DesktopGlyph? = null, suffix: String? = null, invalid: Boolean = false) {
    Row(modifier.inputSurface(invalid).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) DesktopIcon(glyph, DesktopColors.Muted, Modifier.padding(start = 12.dp).size(15.dp))
        BasicTextField(value, onValueChange, Modifier.weight(1f).onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key in listOf(Key.Enter, Key.NumPadEnter) && !it.isCtrlPressed) { onEnter(); true } else false
        }.padding(horizontal = 12.dp, vertical = 10.dp), readOnly = !enabled, singleLine = true,
            textStyle = SaltTheme.textStyles.main.copy(color = DesktopColors.Text), cursorBrush = SolidColor(DesktopColors.Accent),
            decorationBox = { input -> Box { if (value.isEmpty()) Text(placeholder, color = DesktopColors.Muted); input() } })
        if (suffix != null) Text(suffix, Modifier.padding(end = 12.dp), color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
    }
}

@Composable
internal fun DesktopTextArea(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, placeholder: String = "", invalid: Boolean = false) {
    val scroll = rememberScrollState()
    Box(modifier.inputSurface(invalid)) {
        BasicTextField(value, onChange, Modifier.fillMaxWidth().padding(end = 12.dp).verticalScroll(scroll).padding(12.dp),
            readOnly = !enabled, textStyle = SaltTheme.textStyles.main.copy(color = DesktopColors.Text),
            cursorBrush = SolidColor(DesktopColors.Accent),
            decorationBox = { input -> Box { if (value.isEmpty()) Text(placeholder, color = DesktopColors.Muted); input() } })
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
    }
}
