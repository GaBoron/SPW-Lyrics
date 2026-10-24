package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.timingui.LyricsTimingController
import dev.gaboron.spwlyrics.integration.timingui.TimingPanel

internal enum class MetadataLyricsView(val title: String) {
    STRUCTURED("逐句编辑"), SOURCE("编辑原文"), PREVIEW("歌词预览"), TIMING("打轴")
}

@Composable
internal fun MetadataLyricsPanel(controller: MetadataEditorController, timing: LyricsTimingController,
    view: MetadataLyricsView, selectView: (MetadataLyricsView) -> Unit, modifier: Modifier) {
    val state = controller.state
    val focused = view == MetadataLyricsView.TIMING
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LyricsTabs(MetadataLyricsView.entries.map { it.title }, view.ordinal,
            { selectView(MetadataLyricsView.entries[it]) }, compact = focused)
        LyricsTransition(view, Modifier.weight(1f).fillMaxWidth()) { section ->
            if (section == MetadataLyricsView.TIMING) TimingPanel(timing)
            else LyricsSection(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    when (section) {
                        MetadataLyricsView.STRUCTURED -> MetadataStructuredEditor(controller)
                        MetadataLyricsView.PREVIEW -> MetadataLyricsPreview(state)
                        else -> MetadataTextArea(state.draft.lyrics, controller::lyrics, Modifier.fillMaxSize(),
                            enabled = state.loaded != null && !state.busy, invalid = state.previewError != null,
                            placeholder = "粘贴 TTML、LRC、增强 LRC 或 SPL，也可以从搜索结果或歌词文件导入。")
                    }
                }
            }
        }
        AnimatedVisibility(!focused, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LyricsStatus(state.previewError ?: if (state.validating) "正在解析歌词…"
                else "${state.preview?.lines?.size ?: 0} 行歌词 · 修改保存在草稿中，写入后才会更新歌曲文件。", state.previewError != null)
        }
    }
}
