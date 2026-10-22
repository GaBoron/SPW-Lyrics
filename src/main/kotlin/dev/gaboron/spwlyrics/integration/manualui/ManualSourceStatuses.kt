package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.composeui.DesktopColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ManualSourceStatuses(statuses: Map<LyricsSource, ProviderSearchStatus>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        statuses.values.sortedBy { it.source.priority }.forEach { status ->
            val color = when (status.phase) {
                ProviderSearchPhase.NOT_CHECKED -> DesktopColors.Muted
                ProviderSearchPhase.SEARCHING -> DesktopColors.Accent
                ProviderSearchPhase.AVAILABLE -> Color(0xFF3C9B66)
                ProviderSearchPhase.FAILED -> DesktopColors.Error
                ProviderSearchPhase.TIMED_OUT -> Color(0xFFD6A338)
            }
            TooltipArea(tooltip = {
                Box(Modifier.background(SaltTheme.colors.background, SaltTheme.shapes.medium).padding(10.dp)) {
                    Text(status.detail, style = SaltTheme.textStyles.sub)
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).background(color, CircleShape))
                    Text("${if (status.source == LyricsSource.AMLL) "AMLL" else status.source.displayName} ${status.phase.label}",
                        color = DesktopColors.Muted, style = SaltTheme.textStyles.sub)
                }
            }
        }
    }
}
