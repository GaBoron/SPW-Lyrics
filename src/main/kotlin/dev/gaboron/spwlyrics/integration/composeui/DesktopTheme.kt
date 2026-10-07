package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DesktopColors {
    val Background = Color(0xFFF2F4F6)
    val Surface = Color(0xFFFCFDFD)
    val Subtle = Color(0xFFF6F8F9)
    val Border = Color(0xFFDEE4E7)
    val Text = Color(0xFF202A30)
    val Muted = Color(0xFF748087)
    val Accent = Color(0xFF39765B)
    val AccentHover = Color(0xFF2E654C)
    val Selected = Color(0xFFE6F1EA)
    val Hover = Color(0xFFEDF1F3)
    val Error = Color(0xFFAA4D43)
    val Disabled = Color(0xFFE7EBED)
    val ControlHeight = 38.dp
}

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = lightColors(primary = DesktopColors.Accent, background = DesktopColors.Background,
            surface = DesktopColors.Surface, onSurface = DesktopColors.Text, error = DesktopColors.Error),
        typography = Typography(defaultFontFamily = FontFamily.SansSerif,
            body1 = TextStyle(fontSize = 13.sp, color = DesktopColors.Text),
            body2 = TextStyle(fontSize = 12.sp, color = DesktopColors.Muted)),
        content = content,
    )
}
