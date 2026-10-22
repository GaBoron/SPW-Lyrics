package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
internal fun LyricsTabs(labels: List<String>, selected: Int, select: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Column(Modifier.width(IntrinsicSize.Max).selectable(active, role = Role.Tab) { select(index) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, Modifier.padding(vertical = 12.dp),
                    color = if (active) SaltTheme.colors.text else SaltTheme.colors.subText,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Box(Modifier.fillMaxWidth().height(2.dp)
                    .background(if (active) SaltTheme.colors.highlight else Color.Transparent))
            }
        }
    }
}
