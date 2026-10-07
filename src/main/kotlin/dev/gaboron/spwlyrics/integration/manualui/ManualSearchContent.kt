package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun ManualSearchContent(controller: ManualSearchController) {
    val state = controller.state
    val searchFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(DesktopColors.Background).onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when {
            it.isCtrlPressed && it.key == Key.F -> { searchFocus.requestFocus(); true }
            it.isCtrlPressed && it.key == Key.Enter -> { controller.apply(); true }
            it.key == Key.F5 -> { controller.refreshTrack(); true }
            else -> false
        }
    }) {
        Column(Modifier.weight(1f).padding(24.dp, 20.dp, 24.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("手动歌词搜索", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    ElidedText(state.query?.let {
                        listOf(it.title, it.artists.joinToString(" / ")).filter(String::isNotBlank).joinToString(" · ")
                    } ?: if (state.readingTrack) "正在读取当前歌曲…" else "当前没有正在播放的歌曲", size = 14)
                }
                DesktopButton("批量处理", controller::openBatch, glyph = DesktopGlyph.Library)
                Spacer(Modifier.width(8.dp))
                DesktopButton("刷新当前歌曲", controller::refreshTrack, enabled = !state.readingTrack && !state.mutating,
                    loading = state.readingTrack, glyph = DesktopGlyph.Refresh)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DesktopField(state.keywords, controller::keywords, Modifier.weight(1f).focusRequester(searchFocus),
                    placeholder = "搜索歌曲、歌手或专辑", enabled = !state.readingTrack && !state.mutating,
                    onEnter = controller::search, glyph = DesktopGlyph.Search)
                ManualSourceFilter(state.source, controller::source, !state.readingTrack && !state.mutating)
                DesktopButton(if (state.searching) "搜索中" else "搜索", controller::search,
                    enabled = state.canSearch, primary = true, loading = state.searching, glyph = DesktopGlyph.Search)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val previewWidth = (maxWidth * .35f).coerceAtLeast(340.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManualCandidateList(state, controller::select, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(previewWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManualLyricsPreview(state, Modifier.weight(1f).fillMaxWidth())
                        ManualDelayEditor(state, controller)
                    }
                }
            }
        }
        DesktopDivider()
        Row(Modifier.fillMaxWidth().background(DesktopColors.Subtle).padding(24.dp, 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElidedText(state.status, Modifier.weight(1f).padding(end = 12.dp),
                color = if (state.error) DesktopColors.Error else DesktopColors.Muted, size = 11)
            DesktopButton("恢复自动匹配", controller::useAutomatic, enabled = state.canMutate)
            DesktopButton("切回本地歌词", controller::useLocal, enabled = state.canMutate)
            DesktopButton("应用所选歌词", controller::apply, enabled = state.canMutate && state.selected != null,
                primary = true, glyph = DesktopGlyph.Check)
        }
    }
}
