package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DesktopColors {
    val Background: Color @Composable get() = SaltTheme.colors.background
    val Surface: Color @Composable get() = SaltTheme.colors.subBackground
    val Subtle: Color @Composable get() = SaltTheme.colors.background
    val Border: Color @Composable get() = SaltTheme.colors.text.copy(alpha = .12f)
    val Text: Color @Composable get() = SaltTheme.colors.text
    val Muted: Color @Composable get() = SaltTheme.colors.subText
    val Accent: Color @Composable get() = SaltTheme.colors.highlight
    val Selected: Color @Composable get() = SaltTheme.colors.highlight.copy(alpha = .12f)
    val Hover: Color @Composable get() = SaltTheme.colors.text.copy(alpha = .06f)
    val Error: Color @Composable get() = SaltTheme.colors.error
    val ControlHeight = 38.dp
}

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    SaltTheme(configs = SaltConfigs.default(isDarkTheme = isSystemInDarkTheme())) {
    MaterialTheme(
        colors = lightColors(primary = DesktopColors.Accent, background = DesktopColors.Background,
            surface = DesktopColors.Surface, onSurface = DesktopColors.Text, error = DesktopColors.Error),
        typography = Typography(defaultFontFamily = FontFamily.SansSerif,
            body1 = TextStyle(fontSize = 13.sp, color = DesktopColors.Text),
            body2 = TextStyle(fontSize = 12.sp, color = DesktopColors.Muted)),
        content = content,
    )
    }
}
