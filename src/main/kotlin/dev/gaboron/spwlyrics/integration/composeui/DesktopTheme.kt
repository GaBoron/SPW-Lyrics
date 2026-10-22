package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.runtime.Composable
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme
import androidx.compose.ui.graphics.Color

internal object DesktopColors {
    val Surface: Color @Composable get() = SaltTheme.colors.subBackground
    val Text: Color @Composable get() = SaltTheme.colors.text
    val Muted: Color @Composable get() = SaltTheme.colors.subText
    val Accent: Color @Composable get() = SaltTheme.colors.highlight
    val Error: Color @Composable get() = SaltTheme.colors.error
}

@Composable
internal fun DesktopTheme(isDarkTheme: Boolean, content: @Composable () -> Unit) {
    SaltTheme(configs = SaltConfigs.default(isDarkTheme = isDarkTheme), content = content)
}
