package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.moriafly.salt.ui.Text
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
            ToolColumns(Modifier.weight(1f).fillMaxWidth(), 360.dp,
                main = { ManualCandidateList(state, controller::select, it) }, side = { panel ->
                    Column(panel, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManualLyricsPreview(state, Modifier.weight(1f).fillMaxWidth())
                        ManualDelayEditor(state, controller)
                    }
                })
        }
        DesktopDivider()
        FlowRow(Modifier.fillMaxWidth().background(DesktopColors.Subtle).padding(16.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ElidedText(state.status, Modifier.weight(1f).padding(end = 12.dp),
                color = if (state.error) DesktopColors.Error else DesktopColors.Muted, size = 11)
            DesktopButton("恢复自动匹配", controller::useAutomatic, enabled = state.canMutate)
            DesktopButton("切回本地歌词", controller::useLocal, enabled = state.canMutate)
            DesktopButton("导入元数据…", controller::editMetadata, enabled = !state.busy && state.document != null)
            DesktopButton("应用所选歌词", controller::apply, enabled = state.canMutate && state.selected != null,
                primary = true, glyph = DesktopGlyph.Check)
        }
    }
}
