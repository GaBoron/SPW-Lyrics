package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** Page surfaces stay clear; only Salt's grouped items provide a translucent material. */
@Composable
internal fun LyricsSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    RoundedColumn(modifier, paddingValues = PaddingValues(0.dp), content = content)
}

@Composable
internal fun LyricsSectionTitle(text: String) {
    ItemContainer { Text(text, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub) }
}

@Composable
internal fun LyricsScroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
internal fun LyricsStatus(text: String, error: Boolean = false) {
    Text(text, color = if (error) SaltTheme.colors.error else SaltTheme.colors.subText,
        style = SaltTheme.textStyles.sub)
}

/** Short windows scroll the page instead of squeezing controls into the player or a zero-height list. */
@Composable
internal fun LyricsViewport(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        if (maxHeight < 560.dp) LyricsScroll(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(560.dp)) { content() }
        } else content()
    }
}
