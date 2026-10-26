package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import com.moriafly.salt.ui.SaltTheme

/** Short, bounded transitions respond to navigation without moving the surrounding layout. */
@Composable
internal fun <T> LyricsTransition(value: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Crossfade(value, modifier, animationSpec = tween(180), label = "lyrics content", content = content)
}

/** Adjacent tabs share a horizontal axis, including when a user reverses an unfinished transition. */
@Composable
internal fun <T> LyricsPageTransition(value: T, order: (T) -> Int, modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit) {
    AnimatedContent(value, modifier.clipToBounds(), label = "lyrics page", transitionSpec = {
        val direction = if (order(targetState) > order(initialState)) AnimatedContentTransitionScope.SlideDirection.Left
            else AnimatedContentTransitionScope.SlideDirection.Right
        ((slideIntoContainer(direction, tween(260)) + fadeIn(tween(180, delayMillis = 40))) togetherWith
            (slideOutOfContainer(direction, tween(260)) + fadeOut(tween(140)))).using(null)
    }) { content(it) }
}

/** Animate both replacement content and the space it occupies. */
@Composable
internal fun <T> LyricsLayoutTransition(value: T, modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it }, content: @Composable (T) -> Unit) {
    AnimatedContent(value, modifier, contentKey = contentKey, label = "lyrics layout",
        transitionSpec = {
            ((fadeIn(tween(160, delayMillis = 40)) + slideInVertically(tween(220)) { it / 12 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 12 })).using(
                SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(220) }))
        }, content = { content(it) })
}

@Composable
internal fun Modifier.lyricsSelection(selected: Boolean): Modifier {
    val color by animateColorAsState(if (selected) SaltTheme.colors.highlight.copy(alpha = .12f) else Color.Transparent,
        tween(160), label = "lyrics selection")
    val shape = SaltTheme.shapes.medium
    return background(color, shape).clip(shape)
}
