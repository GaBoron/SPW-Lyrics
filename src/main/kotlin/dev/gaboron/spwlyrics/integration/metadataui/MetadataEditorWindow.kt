package dev.gaboron.spwlyrics.integration.metadataui

import dev.gaboron.spwlyrics.integration.composeui.DesktopToolWindow
import dev.gaboron.spwlyrics.integration.composeui.DesktopWindowContent
import dev.gaboron.spwlyrics.storage.FileAudioMetadataStore
import java.nio.file.Path
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Composes the same frame, theme and lifecycle as the existing lyric windows. */
internal class MetadataEditorWindow(
    readCurrentPath: () -> String?,
    refreshSavedPath: (Path) -> Unit,
    openLyricsSearch: () -> Unit,
    onFailure: (String) -> Unit,
) : AutoCloseable {
    private var controller: MetadataEditorController? = null
    private val window = DesktopToolWindow("SPW Lyrics · 歌曲元数据", "metadata-window-position.json", onFailure,
        preferredWidth = 1320, preferredHeight = 860) {
        val session = MetadataEditorController(FileAudioMetadataStore(), object : MetadataEditorOperations {
            override fun currentAudioPath() = readCurrentPath()
            override fun chooseAudioFile() = MetadataFilePicker.audio()
            override fun chooseLyricsFile() = MetadataFilePicker.lyrics()
            override fun afterSave(path: Path) = refreshSavedPath(path)
            override fun openSearch() = openLyricsSearch()
        })
        controller = session
        DesktopWindowContent({ MetadataEditorContent(session) }, { session.close(); controller = null }, session::requestDiscard)
    }

    fun open() = window.open { controller?.let { if (it.state.loaded == null && !it.state.busy) it.current() } }
    fun importSearch(query: TrackQuery, document: LyricsDocument) = window.open { controller?.importSearch(query, document) }
    override fun close() = window.close()
}
