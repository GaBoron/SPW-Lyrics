package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.gaboron.spwlyrics.integration.composeui.DesktopTextArea

@Composable
internal fun MetadataTextArea(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, placeholder: String = "", invalid: Boolean = false, growWithContent: Boolean = false,
) {
    DesktopTextArea(value, onChange, modifier, enabled, placeholder, invalid, growWithContent)
}
