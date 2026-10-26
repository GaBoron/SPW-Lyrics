package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
internal fun LyricsTabs(labels: List<String>, selected: Int, select: (Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val horizontal by animateDpAsState(if (compact) 8.dp else 12.dp, tween(220), label = "tab padding")
    val vertical by animateDpAsState(if (compact) 4.dp else 12.dp, tween(220), label = "tab height")
    val gap by animateDpAsState(if (compact) 4.dp else 8.dp, tween(220), label = "tab gap")
    val bounds = remember(labels) { mutableStateMapOf<Int, Pair<Float, Int>>() }
    val density = LocalDensity.current
    val selectedBounds = bounds[selected]
    val left by animateDpAsState(with(density) { (selectedBounds?.first ?: 0f).toDp() }, tween(260), label = "tab indicator position")
    val width by animateDpAsState(with(density) { (selectedBounds?.second ?: 0).toDp() }, tween(260), label = "tab indicator width")
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val color by animateColorAsState(if (active) SaltTheme.colors.text else SaltTheme.colors.subText, tween(160), label = "tab ink")
                Box(Modifier.onGloballyPositioned { coordinates ->
                    val value = coordinates.positionInParent().x to coordinates.size.width
                    if (bounds[index] != value) bounds[index] = value
                }.clip(SaltTheme.shapes.medium).selectable(active, role = Role.Tab) { select(index) }
                    .padding(horizontal = horizontal, vertical = vertical).padding(bottom = 2.dp)) {
                    Text(label, color = color, fontWeight = FontWeight.Medium)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = left).width(width).height(2.dp).background(SaltTheme.colors.highlight))
    }
}
