package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.moriafly.salt.ui.SaltTheme

/** Short, bounded transitions respond to navigation without moving the surrounding layout. */
@Composable
internal fun <T> LyricsTransition(value: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Crossfade(value, modifier, animationSpec = tween(180), label = "lyrics content", content = content)
}

@Composable
internal fun Modifier.lyricsSelection(selected: Boolean): Modifier {
    val color by animateColorAsState(if (selected) SaltTheme.colors.highlight.copy(alpha = .12f) else Color.Transparent,
        tween(160), label = "lyrics selection")
    return clip(SaltTheme.shapes.medium).background(color)
}
