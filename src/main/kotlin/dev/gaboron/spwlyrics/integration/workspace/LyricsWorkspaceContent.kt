package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.batchui.BatchProcessingContent
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchContent
import dev.gaboron.spwlyrics.integration.metadataui.MetadataDiscardDialog
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorContent
import dev.gaboron.spwlyrics.integration.metadataui.MetadataWriteDialog

@Composable
internal fun LyricsWorkspaceContent(session: LyricsWorkspaceSession, close: () -> Unit) {
    val pages = rememberSaveableStateHolder()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val focused = session.timingFocused
        val gutter by animateDpAsState(if (focused || maxWidth < 720.dp) 16.dp else 32.dp, tween(220), label = "workspace gutter")
        val width by animateDpAsState(if (focused) maxWidth else minOf(maxWidth, 1280.dp), tween(220), label = "workspace width")
        val gap by animateDpAsState(if (focused) 8.dp else 20.dp, tween(220), label = "workspace density")
        Column(Modifier.width(width).fillMaxHeight().padding(horizontal = gutter).padding(top = gap),
            verticalArrangement = Arrangement.spacedBy(gap)) {
            LyricsWorkspaceHeader(session.page, focused, session::select) { session.requestClose(close) }
            LyricsPageTransition(session.page, { it.ordinal }, Modifier.weight(1f).fillMaxWidth()) { page ->
                pages.SaveableStateProvider(page.name) {
                    LyricsViewport(Modifier.fillMaxSize()) {
                        when (page) {
                            LyricsToolPage.SEARCH -> ManualSearchContent(session.manual)
                            LyricsToolPage.METADATA -> MetadataEditorContent(session.metadata, session.timing, session.lyricsView, session::selectLyricsView)
                            LyricsToolPage.BATCH -> BatchProcessingContent(session.batch, session.batchMetadata)
                        }
                    }
                }
            }
        }
    }
    MetadataDiscardDialog(session.metadata)
    MetadataDiscardDialog(session.batchMetadata)
    MetadataWriteDialog(session.metadata)
    MetadataWriteDialog(session.batchMetadata)
}
