package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.AdaptiveDialogSize
import com.moriafly.salt.ui.dialog.BasicAdaptiveDialog

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun DesktopDialog(dismiss: () -> Unit, content: @Composable () -> Unit) {
    val material = LocalDesktopPopupMaterial.current
    BasicAdaptiveDialog(dismiss, AdaptiveDialogSize.Standard) {
        val bounds = Modifier.clip(SaltTheme.shapes.large)
        val surface = material?.invoke(bounds) ?: bounds.background(SaltTheme.colors.popup)
        Box(surface.padding(24.dp)) { content() }
    }
}
