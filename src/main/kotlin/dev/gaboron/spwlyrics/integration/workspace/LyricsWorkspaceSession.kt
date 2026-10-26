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
import dev.gaboron.spwlyrics.integration.metadataui.MetadataLyricsView
import dev.gaboron.spwlyrics.storage.FileAudioMetadataStore
import dev.gaboron.spwlyrics.application.TimingPlayback
import dev.gaboron.spwlyrics.application.TimingAudioDecoder
import dev.gaboron.spwlyrics.storage.TimingCalibrationStore
import dev.gaboron.spwlyrics.integration.timingui.LyricsTimingController

internal enum class LyricsToolPage(val title: String) {
    SEARCH("手动搜索"), METADATA("元数据编辑"), BATCH("批量管理")
}

/** Controllers belong to a session, never to a tab or a composition. All access is on the EDT. */
internal class LyricsWorkspaceSession(
    manualOperations: ManualSearchOperations,
    metadataOperations: MetadataEditorOperations,
    processor: LyricsBatchProcessor,
    describeFailure: (Throwable) -> String,
    timingPlayback: TimingPlayback,
    calibrationStore: TimingCalibrationStore,
    timingAudio: TimingAudioDecoder,
) : AutoCloseable {
    val manual = ManualSearchController(manualOperations)
    val metadata = MetadataEditorController(FileAudioMetadataStore(), metadataOperations)
    val batchMetadata = MetadataEditorController(FileAudioMetadataStore(), metadataOperations)
    val timing = LyricsTimingController(metadata, timingPlayback, calibrationStore, timingAudio)
    val batch = BatchProcessingController(processor, describeFailure)
    var page by mutableStateOf(LyricsToolPage.SEARCH)
        private set
    var lyricsView by mutableStateOf(MetadataLyricsView.STRUCTURED)
        private set
    val timingFocused get() = page == LyricsToolPage.METADATA && lyricsView == MetadataLyricsView.TIMING

    private val initialized = mutableSetOf<LyricsToolPage>()

    fun select(value: LyricsToolPage, initialize: Boolean = true) {
        if (timingFocused && value != LyricsToolPage.METADATA) timing.leave()
        page = value
        if (!initialized.add(value) || !initialize) return
        when (value) {
            LyricsToolPage.SEARCH -> manual.refreshTrack()
            LyricsToolPage.BATCH -> batch.load()
            LyricsToolPage.METADATA -> if (metadata.state.loaded == null && !metadata.state.busy) metadata.current()
        }
    }

    fun selectLyricsView(value: MetadataLyricsView) {
        if (timingFocused && value != MetadataLyricsView.TIMING) timing.leave()
        lyricsView = value
    }

    fun requestClose(close: () -> Unit) = metadata.requestDiscard { batchMetadata.requestDiscard(close) }
    override fun close() { timing.close(); manual.close(); metadata.close(); batchMetadata.close(); batch.close() }
}
