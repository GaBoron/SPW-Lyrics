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
internal fun MetadataFieldsPanel(controller: MetadataEditorController, modifier: Modifier) {
    val state = controller.state
    LyricsScroll(modifier) {
        AudioMetadataField.entries.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(field.label, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                val multiple = field in listOf(AudioMetadataField.ARTIST, AudioMetadataField.ALBUM_ARTIST, AudioMetadataField.COMPOSER, AudioMetadataField.COMMENT)
                if (multiple) MetadataTextArea(state.draft[field], { controller.field(field, it) },
                    Modifier.fillMaxWidth().height(if (field == AudioMetadataField.COMMENT) 80.dp else 58.dp),
                    enabled = state.loaded != null && !state.busy, placeholder = "填写${field.label}")
                else DesktopField(state.draft[field], { controller.field(field, it) }, Modifier.fillMaxWidth(),
                    enabled = state.loaded != null && !state.busy, placeholder = "填写${field.label}")
            }
        }
        LyricsStatus("多值标签每行一个值。保存时保留封面和其他未编辑标签；失败时恢复原文件。")
        state.loaded?.path?.let { LyricsStatus(it.toString()) }
    }
}
