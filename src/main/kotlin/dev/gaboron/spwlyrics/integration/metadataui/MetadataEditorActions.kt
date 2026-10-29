package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun MetadataEditorActions(controller: MetadataEditorController, focused: Boolean = false) {
    val state = controller.state
    ProvideControlSize(if (focused) ControlSize.Small else ControlSize.Regular) {
        Row(Modifier.fillMaxWidth().animateContentSize(tween(240)), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(focused, enter = fadeIn() + slideInHorizontally { -it / 8 },
                    exit = fadeOut() + slideOutHorizontally { -it / 8 }) {
                    Column {
                        Text(state.loaded?.path?.fileName?.toString() ?: "尚未读取歌曲", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LyricsStatus(state.previewError ?: if (state.dirty && !state.error && !state.busy) "有未保存的修改 · Ctrl+S 写入" else state.status,
                            state.previewError != null || state.error)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(!focused, enter = fadeIn() + slideInHorizontally { -it / 8 },
                    exit = fadeOut() + slideOutHorizontally { -it / 8 }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopButton("在线匹配信息与封面", controller::openSearch, enabled = state.loaded != null && !state.busy, glyph = DesktopGlyph.Search)
                        DesktopButton("导入歌词", controller::importLyrics, enabled = state.loaded != null && !state.busy)
                        MetadataFormatPicker(state.format, controller::format, enabled = !state.busy)
                    }
                }
            }
            DesktopButton("写入歌曲文件", controller::save, enabled = state.canSave, primary = true, glyph = DesktopGlyph.Check)
        }
    }
}
