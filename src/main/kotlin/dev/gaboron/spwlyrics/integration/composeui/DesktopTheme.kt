package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme
import androidx.compose.ui.graphics.Color

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
}

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    SaltTheme(configs = SaltConfigs.default(isDarkTheme = isSystemInDarkTheme()), content = content)
}
