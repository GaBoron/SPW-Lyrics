package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.SaltTheme
import dev.gaboron.spwlyrics.integration.batchui.BatchProcessingContent
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchContent
import dev.gaboron.spwlyrics.integration.metadataui.MetadataDiscardDialog
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorContent

@Composable
internal fun LyricsWorkspaceContent(session: LyricsWorkspaceSession, close: () -> Unit) {
    val pages = rememberSaveableStateHolder()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = if (maxWidth < 720.dp) 16.dp else 32.dp
        Column(Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("歌词管理", style = SaltTheme.textStyles.largeTitle, modifier = Modifier.weight(1f))
                DesktopButton("返回", { session.requestClose(close) }, glyph = DesktopGlyph.Back)
            }
            LyricsTabs(LyricsToolPage.entries.map { it.title }, session.page.ordinal,
                { session.select(LyricsToolPage.entries[it]) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                pages.SaveableStateProvider(session.page.name) {
                    LyricsViewport(Modifier.fillMaxSize()) {
                        when (session.page) {
                            LyricsToolPage.SEARCH -> ManualSearchContent(session.manual)
                            LyricsToolPage.METADATA -> MetadataEditorContent(session.metadata)
                            LyricsToolPage.BATCH -> BatchProcessingContent(session.batch)
                        }
                    }
                }
            }
        }
    }
    MetadataDiscardDialog(session.metadata)
}
