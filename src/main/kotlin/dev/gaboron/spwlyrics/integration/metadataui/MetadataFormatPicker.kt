package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.Composable
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu

@Composable
internal fun MetadataFormatPicker(format: MetadataLyricsFormat, select: (MetadataLyricsFormat) -> Unit, enabled: Boolean) {
    LyricsMenu("写入格式：${format.label}", MetadataLyricsFormat.entries.toList(), format, { it.label }, select, enabled = enabled)
}
