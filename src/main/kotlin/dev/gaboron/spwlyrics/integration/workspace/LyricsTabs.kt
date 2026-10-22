package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
internal fun LyricsTabs(labels: List<String>, selected: Int, select: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            val color by animateColorAsState(if (active) SaltTheme.colors.text else SaltTheme.colors.subText, tween(160), label = "tab ink")
            val indicator by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "tab indicator")
            Column(Modifier.width(IntrinsicSize.Max).clip(SaltTheme.shapes.medium)
                .selectable(active, role = Role.Tab) { select(index) }.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, Modifier.padding(vertical = 12.dp),
                    color = color, fontWeight = FontWeight.Medium)
                Box(Modifier.fillMaxWidth().height(2.dp)
                    .graphicsLayer { alpha = indicator; scaleX = .6f + .4f * indicator }.background(SaltTheme.colors.highlight))
            }
        }
    }
}
