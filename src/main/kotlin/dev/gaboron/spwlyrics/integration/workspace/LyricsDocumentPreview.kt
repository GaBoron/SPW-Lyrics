package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricLine

internal fun lyricVoiceLabel(duet: Boolean, background: Boolean): String =
    (if (duet) "对唱 · 右" else "主唱 · 左") + if (background) " · 背景人声" else ""

/** Both tools render the same vocal sides, backgrounds, translations and romanization. */
@Composable
internal fun LyricsDocumentPreview(lines: List<LyricLine>, mainAgent: String, resetKey: Any?, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(resetKey) { list.animateScrollToItem(0) }
    Box(modifier) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = list,
                contentPadding = PaddingValues(20.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(lines) { line ->
                    val duet = !line.agent.isNullOrBlank() && line.agent != mainAgent
                    val align = if (duet) TextAlign.End else TextAlign.Start
                    Column(Modifier.fillMaxWidth().padding(start = if (duet) 24.dp else 0.dp, end = if (duet) 0.dp else 24.dp),
                        horizontalAlignment = if (duet) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(listOfNotNull(line.startMs?.let(SpwLyricsEncoder::timestamp), lyricVoiceLabel(duet, line.background)).joinToString("  ·  "),
                            color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, textAlign = align)
                        Text(line.text, Modifier.fillMaxWidth(), fontSize = if (line.background) 16.sp else 18.sp,
                            lineHeight = 28.sp, textAlign = align,
                            color = if (line.background) SaltTheme.colors.subText else SaltTheme.colors.text)
                        listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).forEach {
                            Text(it, Modifier.fillMaxWidth(), color = SaltTheme.colors.subText, lineHeight = 22.sp, textAlign = align)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
