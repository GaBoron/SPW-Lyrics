package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.integration.workspace.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopField

@Composable
internal fun MetadataFieldsPanel(controller: MetadataEditorController, modifier: Modifier, includeLyrics: Boolean = false) {
    val state = controller.state
    val inputModifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
    LyricsScroll(modifier) {
        MetadataCoverPanel(controller)
        AudioMetadataField.entries.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(field.label, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                val multiple = field in listOf(AudioMetadataField.ARTIST, AudioMetadataField.ALBUM_ARTIST, AudioMetadataField.COMPOSER, AudioMetadataField.LYRICIST, AudioMetadataField.GENRE, AudioMetadataField.COMMENT)
                MetadataImportValue(state.draft[field], state.importBefore?.get(field).orEmpty(),
                    state.importBefore != null, String::isBlank) { value ->
                    if (multiple) MetadataTextArea(value, { controller.field(field, it) }, inputModifier,
                        enabled = state.loaded != null && !state.busy, placeholder = "填写${field.label}", growWithContent = true)
                    else DesktopField(value, { controller.field(field, it) }, inputModifier,
                        enabled = state.loaded != null && !state.busy, placeholder = "填写${field.label}")
                }
            }
        }
        if (includeLyrics) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("内嵌歌词", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
            MetadataImportValue(state.draft.lyrics, state.importBefore?.lyrics.orEmpty(),
                state.importBefore != null, String::isBlank) { value ->
                MetadataTextArea(value, controller::lyrics, Modifier.fillMaxWidth().height(240.dp),
                    enabled = !state.busy, placeholder = "尚无内嵌歌词")
            }
            LyricsStatus("手动修改会取消对应在线项目的勾选；再次勾选可用在线内容替换。")
        }
        LyricsStatus("多值标签每行一个值。保存时保留未编辑的封面和其他标签；失败时恢复原文件。")
        state.loaded?.path?.let { LyricsStatus(it.toString()) }
    }
}
