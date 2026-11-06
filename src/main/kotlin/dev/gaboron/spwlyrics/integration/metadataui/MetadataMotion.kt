package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import dev.gaboron.spwlyrics.integration.workspace.LyricsLayoutTransition

/** Retain outgoing content while fading it out and let surrounding controls follow its size. */
@Composable
internal fun <T> MetadataContentTransition(
    value: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    LyricsLayoutTransition(value, modifier, contentKey, content)
}

/** Only imported changes move: incoming values push old values left; empty slots have no exit. */
@Composable
internal fun <T> MetadataImportValue(value: T, before: T, applying: Boolean, empty: (T) -> Boolean,
    content: @Composable (T) -> Unit) {
    if (!applying || before == value) {
        content(value)
        return
    }
    val progress = remember(value, before) { Animatable(0f) }
    LaunchedEffect(value, before) { progress.animateTo(1f, tween(360)) }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(Modifier.fillMaxWidth().graphicsLayer { translationX = size.width * (1f - progress.value) }) { content(value) }
        if (!empty(before)) Box(Modifier.matchParentSize().graphicsLayer {
            translationX = -size.width * progress.value
        }) { content(before) }
    }
}
