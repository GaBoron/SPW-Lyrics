package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.batchui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.manualui.*
import dev.gaboron.spwlyrics.integration.metadataui.MetadataDiscardDialog
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorContent
import dev.gaboron.spwlyrics.integration.metadataui.MetadataWriteDialog
import dev.gaboron.spwlyrics.integration.metadataui.MetadataTutorialDemo
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialHost
import dev.gaboron.spwlyrics.integration.metadataui.MetadataTutorialStep

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
                    when (page) {
                        LyricsToolPage.SEARCH -> ManualTutorialPage(session)
                        LyricsToolPage.METADATA -> MetadataTutorialPage(session)
                        LyricsToolPage.BATCH -> BatchTutorialPage(session)
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

@Composable
private fun MetadataTutorialPage(session: LyricsWorkspaceSession) {
    val state = session.metadata.state
    TutorialHost(session.metadataTutorial,
        available = session.page == LyricsToolPage.METADATA && !state.busy && state.onlineImport == null && state.pendingWrite == null && state.pendingDiscard == null,
        createDemo = ::MetadataTutorialDemo) { step, demo ->
        key(demo) {
            LyricsViewport(Modifier.fillMaxSize()) {
                val editor = if (step == MetadataTutorialStep.ONLINE_PREVIEW) demo?.importingEditor else demo?.editor
                MetadataEditorContent(editor ?: session.metadata, demo?.timing ?: session.timing,
                    session.lyricsView, session::selectLyricsView, step)
            }
        }
    }
}

@Composable
private fun ManualTutorialPage(session: LyricsWorkspaceSession) {
    TutorialHost(session.manualTutorial,
        available = session.page == LyricsToolPage.SEARCH && !session.manual.state.mutating && !session.manual.state.metadataOnly,
        createDemo = ::ManualTutorialDemo) { step, demo ->
        key(demo) {
            LyricsViewport(Modifier.fillMaxSize()) { ManualSearchContent(demo?.controller ?: session.manual, step) }
        }
    }
}

@Composable
private fun BatchTutorialPage(session: LyricsWorkspaceSession) {
    val state = session.batchMetadata.state
    TutorialHost(session.batchTutorial,
        available = session.page == LyricsToolPage.BATCH && !session.batch.state.active && !state.busy && state.pendingDiscard == null && state.pendingWrite == null,
        createDemo = ::BatchTutorialDemo) { step, demo ->
        val controller = remember(demo, step?.scene) { demo?.controller(step?.scene ?: BatchTutorialScene.READY) }
        key(demo) {
            LyricsViewport(Modifier.fillMaxSize()) {
                BatchProcessingContent(controller ?: session.batch, demo?.metadata?.editor ?: session.batchMetadata, step)
            }
        }
    }
}
