package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
internal fun DesktopCheck(checked: Boolean, onChange: () -> Unit, enabled: Boolean, text: String = "", modifier: Modifier = Modifier) {
    com.moriafly.salt.ui.ItemCheck(checked, { onChange() }, text,
        modifier.widthIn(max = if (text.isBlank()) 44.dp else 190.dp), enabled)
}
