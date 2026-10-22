package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.gaboron.spwlyrics.codec.LyricsTimingOffset
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun ManualLyricsPreview(state: ManualSearchState, modifier: Modifier) {
    val lines = remember(state.document, state.delayValue, state.savedDelay) {
        state.document?.let { LyricsTimingOffset.shift(it, state.delayValue ?: state.savedDelay).lines }.orEmpty()
    }
    LyricsSection(modifier) {
        LyricsSectionTitle("歌词预览")
        val mode = when { state.selected == null -> 0; state.previewing -> 1; lines.isEmpty() -> 2; else -> 3 }
        LyricsTransition(mode, Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> EmptyWorkspace("选择一份歌词预览", "在搜索结果中选择歌曲", glyph = DesktopGlyph.Lyrics)
                1 -> EmptyWorkspace("正在加载歌词", loading = true)
                2 -> EmptyWorkspace("这份歌词没有可预览内容", glyph = DesktopGlyph.Lyrics)
                else -> LyricsDocumentPreview(lines, state.previewMainAgent, state.selected, Modifier.fillMaxSize())
            }
        }
    }
}
