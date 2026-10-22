package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.LocalIndication
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
    SaltTheme(configs = SaltConfigs.default(isDarkTheme = isDarkTheme)) {
        val scrollbar = LocalScrollbarStyle.current.copy(
            unhoverColor = SaltTheme.colors.subText.copy(alpha = .35f),
            hoverColor = SaltTheme.colors.subText.copy(alpha = .7f), hoverDurationMillis = 160)
        CompositionLocalProvider(LocalScrollbarStyle provides scrollbar,
            LocalIndication provides SaltTheme.configs.indication, content = content)
    }
}
