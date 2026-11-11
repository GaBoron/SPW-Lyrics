package dev.gaboron.spwlyrics.integration.metadataui

import dev.gaboron.spwlyrics.application.TimingPlayback
import dev.gaboron.spwlyrics.application.TimingPlaybackSnapshot
import dev.gaboron.spwlyrics.application.CalibrationBeatAudio
import dev.gaboron.spwlyrics.codec.TtmlEditCodec
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialSong
import dev.gaboron.spwlyrics.integration.timingui.LyricsTimingController
import dev.gaboron.spwlyrics.storage.*
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** In-memory song and transport for onboarding. No file picker, network, audio or host playback is used. */
internal class MetadataTutorialDemo : AutoCloseable {
    private val song = TutorialSong.metadata
    private val snapshot = AudioMetadataSnapshot(TutorialSong.path,
        AudioFileRevision(0, FileTime.fromMillis(0), null), song)
    private val document = TutorialSong.document
    private val structured = TtmlEditCodec.openRaw(song.lyrics)
    private val first = structured.lines.first().id
    private val initial = MetadataEditorState(loaded = snapshot, draft = song, preview = document, structured = structured,
        selectedLineId = first, selectedLineIds = setOf(first), selectionAnchor = first,
        status = "教程虚拟歌曲 · 仅供演示，不会读取或写入真实文件。")
    private val store = object : AudioMetadataStore {
        override fun read(path: Path) = snapshot
        override fun save(original: AudioMetadataSnapshot, edited: AudioMetadata): AudioMetadataSaved =
            error("教程示例不写入文件。")
    }
    private val operations = object : MetadataEditorOperations {
        override fun currentAudioPath(): String? = null
        override fun chooseAudioFile(): Path? = null
        override fun chooseLyricsFile(): Path? = null
        override fun chooseCoverFile(): Path? = null
        override fun afterSave(snapshot: AudioMetadataSnapshot, lyricsChanged: Boolean) = Unit
        override fun openSearch(query: TrackQuery) = Unit
    }
    val editor = MetadataEditorController(store, operations, initialState = initial)
    val importingEditor = MetadataEditorController(store, operations, initialState = initial.copy(
        onlineImport = OnlineMetadataImport("示例在线来源", song.fields + (AudioMetadataField.YEAR to "2026"),
            cover = song.cover, lyricsText = song.lyrics, lyricsDocument = document).selectFor(song)))
    val timing = LyricsTimingController(editor, object : TimingPlayback {
        override fun read(expectedPath: Path) = TimingPlaybackSnapshot("tutorial", snapshot.path.toString(), 6000, 24000, false, 1f)
        override fun pauseForCalibration() = Unit
        override fun play(expectedPath: Path) = Unit
        override fun pause(expectedPath: Path) = Unit
        override fun seek(expectedPath: Path, positionMs: Long) = Unit
        override fun rate(expectedPath: Path, value: Float) = Unit
        override fun restoreRate(value: Float) = Unit
    }, createBeatAudio = { object : CalibrationBeatAudio {
        override fun play(bpm: Int, beats: Int, cancelled: () -> Boolean) = Unit
        override fun positionMs(): Double? = null
        override val device = "教程示例"
        override fun close() = Unit
    } })

    override fun close() {
        timing.close()
        editor.close()
        importingEditor.close()
    }

}
