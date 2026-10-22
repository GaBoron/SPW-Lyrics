package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.moriafly.salt.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
internal fun DesktopCheck(checked: Boolean, onChange: () -> Unit, enabled: Boolean, text: String = "", modifier: Modifier = Modifier) {
    com.moriafly.salt.ui.ItemCheck(checked, { onChange() }, text,
        modifier.widthIn(min = if (text.isBlank()) 44.dp else 230.dp, max = if (text.isBlank()) 44.dp else 300.dp), enabled)
}
