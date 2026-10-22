package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.runtime.Composable
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu

@Composable
internal fun ManualSourceFilter(source: LyricsSource?, select: (LyricsSource?) -> Unit, enabled: Boolean) {
    LyricsMenu(source?.displayName ?: "全部来源", listOf(null) + LyricsSource.entries.filter { it != LyricsSource.LOCAL },
        source, { it?.displayName ?: "全部来源" }, select, enabled = enabled)
}
