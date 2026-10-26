package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.composeui.*

@Composable
internal fun LyricsWorkspaceHeader(page: LyricsToolPage, compact: Boolean, select: (LyricsToolPage) -> Unit, close: () -> Unit) {
    val height by animateDpAsState(if (compact) 40.dp else 108.dp, tween(260), label = "header height")
    val tabTop by animateDpAsState(if (compact) 0.dp else 56.dp, tween(260), label = "navigation position")
    val trailing by animateDpAsState(if (compact) 88.dp else 0.dp, tween(260), label = "navigation clearance")
    Box(Modifier.fillMaxWidth().height(height)) {
        AnimatedVisibility(!compact, enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Text("歌词管理", style = SaltTheme.textStyles.largeTitle)
        }
        LyricsTabs(LyricsToolPage.entries.map { it.title }, page.ordinal, { select(LyricsToolPage.entries[it]) },
            Modifier.fillMaxWidth().offset(y = tabTop).padding(end = trailing), compact = compact)
        ProvideControlSize(if (compact) ControlSize.Small else ControlSize.Regular) {
            DesktopButton("退出", close, Modifier.align(Alignment.TopEnd), glyph = DesktopGlyph.Exit)
        }
    }
}
