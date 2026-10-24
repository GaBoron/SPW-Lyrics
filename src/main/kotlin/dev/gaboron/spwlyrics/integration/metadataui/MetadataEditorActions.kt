package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
@OptIn(UnstableSaltUiApi::class)
internal fun MetadataEditorActions(controller: MetadataEditorController, focused: Boolean = false) {
    val state = controller.state
    val uri = LocalUriHandler.current
    LyricsLayoutTransition(focused, Modifier.fillMaxWidth()) { timing ->
        if (timing) ProvideControlSize(ControlSize.Small) {
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                ItemContainer {
                    JustifiedRow(startContent = {
                        Column {
                            Text(state.loaded?.path?.fileName?.toString() ?: "尚未读取歌曲", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LyricsStatus(state.previewError ?: if (state.dirty && !state.error && !state.busy) "有未保存的修改 · Ctrl+S 写入" else state.status,
                                state.previewError != null || state.error)
                        }
                    }, endContent = {
                        DesktopButton("写入歌曲文件", controller::save, enabled = state.canSave, primary = true, glyph = DesktopGlyph.Check)
                    }, verticalAlignment = Alignment.CenterVertically)
                }
            }
        } else LyricsActionBar {
            DesktopButton("在线匹配信息与封面", controller::openSearch, enabled = state.loaded != null && !state.busy, glyph = DesktopGlyph.Search)
            DesktopButton("导入歌词", controller::importLyrics, enabled = state.loaded != null && !state.busy)
            MetadataFormatPicker(state.format, controller::format, enabled = !state.busy)
            DesktopButton("转换格式", controller::convert, enabled = state.loaded != null && !state.busy && !state.validating && state.preview != null)
            DesktopButton("AMLL 编辑器", { uri.openUri("https://amll-ttml-tool.stevexmh.net/") })
            DesktopButton("写入歌曲文件", controller::save, enabled = state.canSave, primary = true, glyph = DesktopGlyph.Check)
        }
    }
}
