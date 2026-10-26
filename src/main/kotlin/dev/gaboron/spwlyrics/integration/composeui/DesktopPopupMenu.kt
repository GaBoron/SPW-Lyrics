package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.popup.PopupMenu

internal val LocalDesktopPopupMaterial = staticCompositionLocalOf<(@Composable (Modifier) -> Modifier)?> { null }

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun DesktopPopupMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val material = LocalDesktopPopupMaterial.current
    val modifier = Modifier.clip(SaltTheme.shapes.medium)
    PopupMenu(expanded, dismiss, modifier = material?.invoke(modifier) ?: modifier,
        backgroundColor = if (material != null) Color.Transparent else SaltTheme.colors.popup, content = content)
}
