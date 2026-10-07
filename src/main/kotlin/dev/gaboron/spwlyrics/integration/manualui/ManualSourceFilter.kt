package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun ManualSourceFilter(source: LyricsSource?, select: (LyricsSource?) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val offset = with(LocalDensity.current) { 43.dp.roundToPx() }
    Box {
        DesktopButton(source?.displayName ?: "全部在线来源", { expanded = true }, Modifier.width(168.dp),
            enabled = enabled, glyph = DesktopGlyph.Chevron)
        if (expanded && enabled) Popup(alignment = Alignment.TopStart, offset = IntOffset(0, offset),
            onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(190.dp).desktopPanel().padding(5.dp)) {
                (listOf<LyricsSource?>(null) + LyricsSource.entries.filter { it != LyricsSource.LOCAL }).forEach { choice ->
                    DesktopButton(choice?.displayName ?: "全部在线来源", { select(choice); expanded = false },
                        Modifier.fillMaxWidth(), glyph = if (source == choice) DesktopGlyph.Check else null)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}
