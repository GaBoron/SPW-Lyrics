package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.Composable
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
