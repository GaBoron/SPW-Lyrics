package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun ManualMetadataViewer(state: ManualSearchState, maxBodyHeight: Dp) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val candidate = state.selected?.candidate
    val groups = remember(candidate, state.lyricsMetadata) { ManualMetadataPresentation.groups(candidate, state.lyricsMetadata) }
    val count = groups.sumOf { it.entries.size }
    val scroll = rememberScrollState()
    LaunchedEffect(candidate?.source, candidate?.remoteId) { scroll.scrollTo(0) }
    LyricsSection {
        Item({ expanded = !expanded }, text = "元数据查看", sub = when {
            candidate == null -> "选择一份歌词查看"
            state.previewing -> "正在加载歌词元数据…"
            count == 0 -> "暂无元数据"
            else -> "$count 项"
        })
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(Modifier.fillMaxWidth().heightIn(max = maxBodyHeight)) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(16.dp).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LyricsLayoutTransition(groups) { current ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (current.isEmpty()) LyricsStatus(if (state.previewing) "正在加载…" else "当前预览没有可显示的元数据。")
                                current.forEach { group ->
                                    Text(group.title, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                    group.entries.forEach { (label, value) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(label, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                            Text(value)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize()) {
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}
