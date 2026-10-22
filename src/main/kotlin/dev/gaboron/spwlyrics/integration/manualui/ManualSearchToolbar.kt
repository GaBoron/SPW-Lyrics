package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

/** A compact search desk leaves the page's width and height to candidates and lyrics. */
@Composable
internal fun ManualSearchToolbar(controller: ManualSearchController, searchFocus: FocusRequester) {
    val state = controller.state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val track: @Composable (Modifier) -> Unit = { modifier ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.query?.title ?: "先播放一首歌曲", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.query?.artists?.joinToString(" / ").orEmpty().ifBlank { "搜索并选择要使用的歌词" },
                            color = DesktopColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DesktopButton("刷新歌曲", controller::refreshTrack, enabled = !state.readingTrack && !state.mutating,
                        loading = state.readingTrack, glyph = DesktopGlyph.Refresh)
                }
            }
            val search: @Composable (Modifier, Modifier) -> Unit = { modifier, input ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopField(state.keywords, controller::keywords, input.focusRequester(searchFocus),
                        placeholder = "歌曲、歌手或专辑", enabled = !state.readingTrack && !state.mutating,
                        onEnter = controller::search, glyph = DesktopGlyph.Search)
                    ManualSourceFilter(state.source, controller::source, !state.readingTrack && !state.mutating)
                    DesktopButton("搜索", controller::search, enabled = state.canSearch, primary = true, loading = state.searching)
                }
            }
            if (maxWidth >= 900.dp) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                track(Modifier.weight(1f))
                search(Modifier, Modifier.width(280.dp))
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                track(Modifier.fillMaxWidth())
                search(Modifier.widthIn(max = 560.dp).fillMaxWidth(), Modifier.weight(1f))
            }
        }
        ManualSourceStatuses(state.sourceStatuses)
        LyricsStatus(state.status, state.error)
    }
}
