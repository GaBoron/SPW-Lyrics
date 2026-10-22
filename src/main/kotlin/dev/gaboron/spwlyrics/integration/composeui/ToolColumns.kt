package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stack the panels in a narrow host instead of imposing a desktop window's minimum width. */
@Composable
internal fun ToolColumns(modifier: Modifier, sideWidth: Dp, sideFirst: Boolean = false,
    main: @Composable (Modifier) -> Unit, side: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 850.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sideFirst) side(Modifier.width(sideWidth).fillMaxHeight())
            main(Modifier.weight(1f).fillMaxHeight())
            if (!sideFirst) side(Modifier.width(sideWidth).fillMaxHeight())
        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            main(Modifier.weight(1f).fillMaxWidth())
            side(Modifier.weight(.65f).fillMaxWidth())
        }
    }
}
