package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Retain outgoing content while fading it out and let surrounding controls follow its size. */
@Composable
internal fun <T> MetadataContentTransition(
    value: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(value, modifier, contentKey = contentKey, label = "metadata content",
        transitionSpec = {
            (fadeIn(tween(160, delayMillis = 60)) togetherWith fadeOut(tween(120))).using(
                SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(220, easing = FastOutSlowInEasing) })
            )
        }, content = { content(it) })
}
