package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.lyricsSelection

@Composable
internal fun ManualCandidateRow(row: CandidateScore, selected: Boolean, enabled: Boolean, click: () -> Unit) {
    val checked by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "candidate check")
    Row(Modifier.fillMaxWidth().lyricsSelection(selected)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = click).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.candidate.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(row.candidate.artists.joinToString(" / "), row.candidate.album).filter(String::isNotBlank).joinToString(" · "),
                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${CandidateColumn.SOURCE.text(row)} · ${CandidateColumn.QUALITY.text(row)} · ${CandidateColumn.DURATION.text(row)} · 匹配 ${CandidateColumn.SCORE.text(row)}",
                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Keep the marker's geometry even while it fades out; selection never moves the text.
        DesktopIcon(DesktopGlyph.Check, SaltTheme.colors.highlight, Modifier.size(18.dp).graphicsLayer { alpha = checked; scaleX = checked; scaleY = checked })
    }
}
