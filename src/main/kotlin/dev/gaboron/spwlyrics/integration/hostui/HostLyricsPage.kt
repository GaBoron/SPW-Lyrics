package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.DesktopTheme
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspaceContent
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspaceSession

/** Input belongs to the workspace above the player's reserved area. */
@Composable
internal fun HostLyricsPage(workspace: LyricsWorkspace, isDarkTheme: Boolean, session: LyricsWorkspaceSession) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(session) { focus.requestFocus() }
    WorkspaceInputSurface(Modifier.fillMaxSize().padding(bottom = 96.dp).focusRequester(focus)
        .onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                workspace.requestLeave {}; true
            } else false
        }.focusGroup().focusable()) {
        DesktopTheme(isDarkTheme) {
            LyricsWorkspaceContent(session, workspace::dismiss)
        }
    }
}
