package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun LyricsWorkspaceHeader(page: LyricsToolPage, compact: Boolean, select: (LyricsToolPage) -> Unit, back: () -> Unit) {
    LyricsLayoutTransition(compact, Modifier.fillMaxWidth()) { focused ->
        ProvideControlSize(if (focused) ControlSize.Small else ControlSize.Regular) {
            val navigation: @Composable () -> Unit = {
                LyricsTabs(LyricsToolPage.entries.map { it.title }, page.ordinal,
                    { select(LyricsToolPage.entries[it]) }, compact = focused)
            }
            val backButton: @Composable () -> Unit = {
                DesktopButton("返回", back, glyph = DesktopGlyph.Back)
            }
            if (focused) JustifiedRow(startContent = navigation, endContent = backButton,
                verticalAlignment = Alignment.CenterVertically)
            else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                JustifiedRow(startContent = { Text("歌词管理", style = SaltTheme.textStyles.largeTitle) },
                    endContent = backButton, verticalAlignment = Alignment.CenterVertically)
                navigation()
            }
        }
    }
}
