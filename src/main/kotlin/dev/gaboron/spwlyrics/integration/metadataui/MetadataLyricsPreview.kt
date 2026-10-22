package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun MetadataLyricsPreview(state: MetadataEditorState) {
    val mode = when { state.previewError != null -> 0; state.preview != null -> 1; state.validating -> 2; else -> 3 }
    LyricsTransition(mode, Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> EmptyWorkspace("歌词无法预览", "回到编辑原文检查格式", glyph = DesktopGlyph.Lyrics)
            1 -> LyricsDocumentPreview(state.preview?.lines.orEmpty(), state.previewMainAgent, state.loaded?.path, Modifier.fillMaxSize())
            2 -> EmptyWorkspace("正在解析歌词", loading = true)
            else -> EmptyWorkspace("尚无内嵌歌词", "导入歌词文件或从搜索结果写入", glyph = DesktopGlyph.Lyrics)
        }
    }
}
