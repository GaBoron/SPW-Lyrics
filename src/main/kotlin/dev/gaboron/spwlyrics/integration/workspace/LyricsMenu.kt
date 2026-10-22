package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph

@Composable
@OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
internal fun <T> LyricsMenu(label: String, choices: List<T>, selected: T, title: (T) -> String,
    select: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        DesktopButton(label, { expanded = true }, enabled = enabled, glyph = DesktopGlyph.Chevron)
        PopupMenu(expanded && enabled, { expanded = false }) {
            choices.forEach { choice ->
                PopupMenuItem(onClick = { select(choice); expanded = false }, selected = choice == selected, text = title(choice))
            }
        }
    }
}
