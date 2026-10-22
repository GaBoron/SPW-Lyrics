package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        } else {
            val scroll = rememberScrollState()
            val mainHeight = (maxHeight * .6f).coerceAtLeast(280.dp)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    main(Modifier.height(mainHeight).fillMaxWidth())
                    side(Modifier.height(340.dp).fillMaxWidth())
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
