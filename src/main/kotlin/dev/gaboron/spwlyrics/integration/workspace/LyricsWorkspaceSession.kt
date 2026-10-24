package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import dev.gaboron.spwlyrics.application.LyricsBatchProcessor
import dev.gaboron.spwlyrics.integration.batchui.BatchProcessingController
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchController
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchOperations
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorController
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorOperations
import dev.gaboron.spwlyrics.storage.FileAudioMetadataStore

internal enum class LyricsToolPage(val title: String) {
    SEARCH("手动搜索"), METADATA("元数据编辑"), BATCH("批量处理")
}

/** Controllers belong to a session, never to a tab or a composition. All access is on the EDT. */
internal class LyricsWorkspaceSession(
    manualOperations: ManualSearchOperations,
    metadataOperations: MetadataEditorOperations,
    processor: LyricsBatchProcessor,
    describeFailure: (Throwable) -> String,
) : AutoCloseable {
    val manual = ManualSearchController(manualOperations)
    val metadata = MetadataEditorController(FileAudioMetadataStore(), metadataOperations)
    val batch = BatchProcessingController(processor, describeFailure)
    var page by mutableStateOf(LyricsToolPage.SEARCH)
        private set

    private val initialized = mutableSetOf<LyricsToolPage>()

    fun select(value: LyricsToolPage, initialize: Boolean = true) {
        page = value
        if (!initialized.add(value) || !initialize) return
        when (value) {
            LyricsToolPage.SEARCH -> manual.refreshTrack()
            LyricsToolPage.BATCH -> batch.load()
            LyricsToolPage.METADATA -> if (metadata.state.loaded == null && !metadata.state.busy) metadata.current()
        }
    }

    fun requestClose(close: () -> Unit) = metadata.requestDiscard(close)
    override fun close() { manual.close(); metadata.close(); batch.close() }
}
