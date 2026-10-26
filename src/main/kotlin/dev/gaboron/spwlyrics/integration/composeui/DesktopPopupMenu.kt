package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.popup.PopupMenu

internal val LocalDesktopPopupMaterial = staticCompositionLocalOf<(@Composable (Modifier) -> Modifier)?> { null }

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun DesktopPopupMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val material = LocalDesktopPopupMaterial.current
    val transition = updateTransition(expanded, label = "popup visibility")
    val visible by transition.animateFloat(transitionSpec = { tween(if (targetState) 180 else 120) },
        label = "popup surface") { if (it) 1f else 0f }
    // Keep the material inside the animated layer; Salt's inner animation only wraps menu content.
    val modifier = Modifier.graphicsLayer {
        alpha = visible
        scaleX = .96f + .04f * visible
        scaleY = .88f + .12f * visible
        transformOrigin = TransformOrigin(.5f, 0f)
    }.clip(SaltTheme.shapes.medium)
    // The popup stays mounted until the entire surface has finished leaving, including a reversed entrance.
    if (transition.currentState || transition.targetState || transition.isRunning) {
        PopupMenu(true, dismiss, modifier = material?.invoke(modifier) ?: modifier,
            backgroundColor = if (material != null) Color.Transparent else SaltTheme.colors.popup, content = content)
    }
}
