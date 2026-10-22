package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.batchui.BatchProcessingContent
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchContent
import dev.gaboron.spwlyrics.integration.metadataui.MetadataDiscardDialog
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorContent

@Composable
internal fun LyricsWorkspaceContent(session: LyricsWorkspaceSession, close: () -> Unit) {
    Column(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("歌词管理", Modifier.padding(horizontal = 8.dp))
            LyricsToolPage.entries.forEach { page ->
                DesktopButton(page.title, { session.select(page) }, primary = session.page == page)
            }
            DesktopButton("返回", { session.requestClose(close) })
        }
        DesktopDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (session.page) {
                LyricsToolPage.SEARCH -> ManualSearchContent(session.manual)
                LyricsToolPage.METADATA -> MetadataEditorContent(session.metadata)
                LyricsToolPage.BATCH -> BatchProcessingContent(session.batch)
            }
        }
    }
    MetadataDiscardDialog(session.metadata)
}
