package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.EditableLyricWord
import dev.gaboron.spwlyrics.integration.composeui.desktopControlMotion
import dev.gaboron.spwlyrics.integration.workspace.LyricsTransition

@Composable
internal fun TimingWordCell(word: EditableLyricWord, selected: Boolean, playing: Boolean, enabled: Boolean,
    choose: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(when { selected -> SaltTheme.colors.highlight.copy(alpha = .27f)
        playing -> SaltTheme.colors.highlight.copy(alpha = .09f); else -> Color.Transparent },
        tween(120), label = "playing word")
    val edge by animateColorAsState(if (selected) SaltTheme.colors.highlight else SaltTheme.colors.stroke,
        tween(160), label = "timing cursor")
    Column(modifier.widthIn(min = 94.dp, max = 220.dp).width(IntrinsicSize.Max)
        .clip(SaltTheme.shapes.small).background(fill).border(if (selected) 2.dp else 1.dp, edge, SaltTheme.shapes.small)
        .desktopControlMotion(enabled).selectable(selected, enabled = enabled, role = Role.Button, onClick = choose)
        .padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TimingTimeLabel(word.start, start = true)
        Text(word.text, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, modifier = Modifier.padding(vertical = 2.dp))
        TimingTimeLabel(word.end, start = false)
    }
}

@Composable
internal fun TimingTimeLabel(value: String, start: Boolean) {
    val text = remember(value) { runCatching { LyricEditTime.format(LyricEditTime.parse(value)) }.getOrDefault(value).ifBlank { "--:--.---" } }
    val dark = SaltTheme.configs.isDarkTheme
    val ink = if (start) { if (dark) Color(0xFF88D9B8) else Color(0xFF176847) }
        else { if (dark) Color(0xFFF0A2B3) else Color(0xFF992E49) }
    LyricsTransition(text) { label ->
        Text(label, color = ink, style = SaltTheme.textStyles.sub.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            maxLines = 1, modifier = Modifier.clip(SaltTheme.shapes.small).background(ink.copy(alpha = .13f))
                .padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
