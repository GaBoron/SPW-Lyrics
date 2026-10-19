package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun MetadataFormatPicker(format: MetadataLyricsFormat, select: (MetadataLyricsFormat) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val offset = with(LocalDensity.current) { 43.dp.roundToPx() }
    Box {
        DesktopButton(format.label, { expanded = true }, Modifier.width(128.dp), enabled = enabled, glyph = DesktopGlyph.Chevron)
        if (expanded && enabled) Popup(alignment = Alignment.TopStart, offset = IntOffset(0, offset),
            onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(155.dp).desktopPanel().padding(5.dp)) {
                MetadataLyricsFormat.entries.forEach { choice ->
                    DesktopButton(choice.label, { select(choice); expanded = false }, Modifier.fillMaxWidth(),
                        glyph = if (format == choice) DesktopGlyph.Check else null)
                }
            }
        }
    }
}
