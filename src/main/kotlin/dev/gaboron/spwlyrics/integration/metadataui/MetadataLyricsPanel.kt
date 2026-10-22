package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun MetadataLyricsPanel(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    var view by rememberSaveable { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LyricsTabs(listOf("逐句编辑", "编辑原文", "歌词预览"), view, { view = it })
        LyricsSection(Modifier.weight(1f)) {
            LyricsTransition(view, Modifier.fillMaxSize().padding(16.dp)) { section ->
                when (section) {
                    0 -> MetadataStructuredEditor(controller)
                    2 -> MetadataLyricsPreview(state)
                    else -> MetadataTextArea(state.draft.lyrics, controller::lyrics, Modifier.fillMaxSize(),
                        enabled = state.loaded != null && !state.busy, invalid = state.previewError != null,
                        placeholder = "粘贴 TTML、LRC、增强 LRC 或 SPL，也可以从搜索结果或歌词文件导入。")
                }
            }
        }
        LyricsStatus(state.previewError ?: if (state.validating) "正在解析歌词…"
            else "${state.preview?.lines?.size ?: 0} 行歌词 · 修改保存在草稿中，写入后才会更新歌曲文件。", state.previewError != null)
    }
}
