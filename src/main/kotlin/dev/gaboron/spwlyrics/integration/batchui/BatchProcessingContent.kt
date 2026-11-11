package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchState
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorController
import java.nio.file.Path
import dev.gaboron.spwlyrics.integration.tutorialui.tutorialTarget

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun BatchProcessingContent(controller: BatchProcessingActions, editor: MetadataEditorController, tutorial: BatchTutorialStep? = null) {
    val state = controller.state
    var editing by remember { mutableStateOf(false) }
    var writeOptions by remember { mutableStateOf(false) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shownEditing = tutorial?.let { it == BatchTutorialStep.EDIT } ?: editing
    val pane = updateTransition(shownEditing, label = "batch editor")
    val paneWidth by pane.animateDp(transitionSpec = { tween(240) }, label = "editor and gap") {
        if (it) 376.dp else 0.dp
    }
    LaunchedEffect(pane.currentState, pane.targetState, pane.isRunning, paneWidth, afterClose) {
        if (!pane.currentState && !pane.targetState && !pane.isRunning && paneWidth == 0.dp) {
            afterClose?.let { action -> afterClose = null; editor.clear(); action() }
        }
    }
    val editable = state.editable && !editor.state.busy
    val closeEditor: (() -> Unit) -> Unit = { action ->
        editor.requestDiscard { editing = false; afterClose = action }
    }
    val summary = remember(state.snapshot, state.selectedKeys) { BatchProgressSummary.from(state) }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.F5) { controller.refresh(); true } else false
    }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LyricsStatus("缓存歌词、批量写入所选数据，或点击每首歌旁的编辑图标修改元数据。")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("缓存歌词", { closeEditor { controller.start() } },
                Modifier.tutorialTarget(BatchTutorialTarget.CACHE),
                enabled = editable && state.selectedKeys.isNotEmpty(), primary = !state.active, loading = state.busy)
            DesktopButton("批量写入", { closeEditor { writeOptions = true } },
                Modifier.tutorialTarget(BatchTutorialTarget.WRITE),
                enabled = editable && state.selectedKeys.isNotEmpty(), glyph = DesktopGlyph.Edit)
            androidx.compose.animation.AnimatedVisibility(state.active,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()) {
                Row(Modifier.tutorialTarget(BatchTutorialTarget.CONTROL), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopButton(if (state.snapshot.state == LyricsBatchState.PAUSED) "继续处理" else "暂停", controller::pauseOrResume,
                        enabled = !state.busy, primary = true)
                    DesktopButton("停止", controller::stop, enabled = !state.busy)
                }
            }
            DesktopButton("重试失败项", { closeEditor { controller.retry() } },
                Modifier.tutorialTarget(BatchTutorialTarget.RETRY),
                enabled = editable && state.snapshot.items.any { it.state == LyricsBatchItemState.FAILED })
            DesktopButton("刷新音乐库", controller::refresh, Modifier.tutorialTarget(BatchTutorialTarget.REFRESH), enabled = editable, glyph = DesktopGlyph.Refresh)
        }
        BatchProgressPanel(state, summary, Modifier.fillMaxWidth().tutorialTarget(BatchTutorialTarget.PROGRESS))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("已选 ${summary.selected} / ${state.snapshot.items.size} 首", Modifier.padding(vertical = 8.dp), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            Box(Modifier.tutorialTarget(BatchTutorialTarget.REPROCESS)) {
                DesktopCheck(state.includeCached, { controller.includeCached(!state.includeCached) }, state.editable, "重新处理已有缓存")
            }
            DesktopButton("全选", { controller.selectAll(true) }, enabled = state.editable && state.keys.isNotEmpty())
            DesktopButton("取消全选", { controller.selectAll(false) }, enabled = state.editable && state.keys.isNotEmpty())
        }
        LyricsStatus(state.message, state.error)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            val list: @Composable (Modifier) -> Unit = { modifier ->
                BatchTrackList(state, controller::select, { query ->
                    if (afterClose == null) {
                        editing = true
                        editor.openPath(Path.of(query.path))
                    }
                }, modifier)
            }
            val fields: @Composable (Modifier) -> Unit = { modifier ->
                BatchTrackEditor(editor, { closeEditor {} }, modifier.tutorialTarget(BatchTutorialTarget.EDIT))
            }
            if (tutorial == BatchTutorialStep.OPTIONS) LyricsSection(Modifier.fillMaxSize()) {
                LyricsScroll(Modifier.fillMaxSize().padding(16.dp)) { BatchMetadataOptionsContent(state.selectedKeys.size, {}, {}) }
            } else if (wide) Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxHeight())
                BatchEditorPane(paneWidth) { fields(Modifier.fillMaxSize()) }
            } else LyricsPageTransition(shownEditing, { if (it) 1 else 0 }, Modifier.fillMaxSize()) { detail ->
                if (detail) fields(Modifier.fillMaxSize()) else list(Modifier.fillMaxSize())
            }
        }
    }
    if (writeOptions) BatchMetadataDialog(state.selectedKeys.size, { writeOptions = false }) {
        writeOptions = false
        controller.start(it)
    }
}
