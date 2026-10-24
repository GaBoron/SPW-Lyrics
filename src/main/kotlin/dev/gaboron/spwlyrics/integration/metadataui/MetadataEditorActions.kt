package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.LyricsActionBar

@Composable
internal fun MetadataEditorActions(controller: MetadataEditorController) {
    val state = controller.state
    val uri = LocalUriHandler.current
    LyricsActionBar {
        DesktopButton("在线匹配信息与封面", controller::openSearch, enabled = state.loaded != null && !state.busy, glyph = DesktopGlyph.Search)
        DesktopButton("导入歌词", controller::importLyrics, enabled = state.loaded != null && !state.busy)
        MetadataFormatPicker(state.format, controller::format, enabled = !state.busy)
        DesktopButton("转换格式", controller::convert, enabled = state.loaded != null && !state.busy && !state.validating && state.preview != null)
        DesktopButton("AMLL 编辑器", { uri.openUri("https://amll-ttml-tool.stevexmh.net/") })
        DesktopButton("写入歌曲文件", controller::save, enabled = state.canSave, primary = true, glyph = DesktopGlyph.Check)
    }
}
