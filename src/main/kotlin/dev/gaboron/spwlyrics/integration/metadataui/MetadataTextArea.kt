package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gaboron.spwlyrics.integration.composeui.DesktopColors

@Composable
internal fun MetadataTextArea(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, placeholder: String = "", invalid: Boolean = false,
) {
    val scroll = rememberScrollState()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier.background(DesktopColors.Surface, RoundedCornerShape(7.dp))
        .border(1.dp, if (invalid) DesktopColors.Error else if (focused) DesktopColors.Accent else DesktopColors.Border,
            RoundedCornerShape(7.dp))) {
        BasicTextField(value, onChange, Modifier.fillMaxSize().padding(11.dp, 9.dp, 20.dp, 9.dp).verticalScroll(scroll),
            enabled = enabled, interactionSource = interaction,
            textStyle = TextStyle(fontSize = 13.sp, lineHeight = 21.sp, color = DesktopColors.Text),
            cursorBrush = SolidColor(DesktopColors.Accent), decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = DesktopColors.Muted, fontSize = 12.sp)
                inner()
            })
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
    }
}
