@file:OptIn(androidx.compose.runtime.InternalComposeApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace

/** Retain the host composition/state while hiding its page ink beneath the transparent workspace. */
@Composable
internal fun HostLyricsSurface(bridge: HostUiBridge, workspace: LyricsWorkspace) {
    val active = workspace.session != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithContent { if (!active) drawContent() }
            .focusProperties { canFocus = !active }
            .pointerInput(active) {
                if (active) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .then(if (active) Modifier.clearAndSetSemantics {} else Modifier)) {
            bridge.HostPage()
            bridge.capture(currentComposer)
        }
        HostLyricsPage(workspace)
    }
}
