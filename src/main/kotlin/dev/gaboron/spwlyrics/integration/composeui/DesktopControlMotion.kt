package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun Modifier.desktopControlMotion(enabled: Boolean): Modifier {
    var pressed by remember(enabled) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120), label = "control press")
    return graphicsLayer { scaleX = scale; scaleY = scale }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try { waitForUpOrCancellation(pass = PointerEventPass.Initial) } finally { pressed = false }
        }
    }
}
