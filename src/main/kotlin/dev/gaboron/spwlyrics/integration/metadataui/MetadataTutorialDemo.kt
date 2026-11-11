package dev.gaboron.spwlyrics.integration.metadataui

import dev.gaboron.spwlyrics.application.TimingPlayback
import dev.gaboron.spwlyrics.application.TimingPlaybackSnapshot
import dev.gaboron.spwlyrics.application.CalibrationBeatAudio
import dev.gaboron.spwlyrics.codec.MetadataLyricsImport
import dev.gaboron.spwlyrics.codec.TtmlEditCodec
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.timingui.LyricsTimingController
import dev.gaboron.spwlyrics.storage.*
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import javax.imageio.ImageIO

/** In-memory song and transport for onboarding. No file picker, network, audio or host playback is used. */
internal class MetadataTutorialDemo : AutoCloseable {
    private val song = AudioMetadata(
        fields = mapOf(AudioMetadataField.TITLE to "星光来信", AudioMetadataField.ARTIST to "示例歌手\n星光合唱团",
            AudioMetadataField.ALBUM to "夜空练习册", AudioMetadataField.ALBUM_ARTIST to "示例歌手",
            AudioMetadataField.GENRE to "流行", AudioMetadataField.TRACK to "1", AudioMetadataField.COMPOSER to "小星",
            AudioMetadataField.LYRICIST to "小光", AudioMetadataField.LANGUAGE to "中文",
            AudioMetadataField.COMMENT to "教程虚拟歌曲，不对应真实音频文件"),
        lyrics = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xml:space="preserve">
              <head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/></metadata></head>
              <body><div>
                <p begin="00:05.000" end="00:09.000" ttm:agent="v1"><span begin="00:05.000" end="00:06.000">星光</span><span begin="00:06.000" end="00:07.000">落在</span><span begin="00:07.000" end="00:09.000">窗前</span><span ttm:role="x-translation">Starlight falls by the window</span><span ttm:role="x-roman">xing guang luo zai chuang qian</span></p>
                <p begin="00:10.000" end="00:14.000" ttm:agent="v2"><span begin="00:10.000" end="00:11.000">把这</span><span begin="00:11.000" end="00:12.500">一刻</span><span begin="00:12.500" end="00:14.000">写成信</span></p>
                <p begin="00:15.000" end="00:19.000" ttm:agent="v1"><span begin="00:15.000" end="00:17.000">跟着</span><span begin="00:17.000" end="00:19.000">旋律前行</span></p>
              </div></body>
            </tt>
        """.trimIndent(), artwork = listOf(cover()),
    )
    private val snapshot = AudioMetadataSnapshot(Path.of("教程示例", "星光来信.flac"),
        AudioFileRevision(0, FileTime.fromMillis(0), null), song)
    private val document = MetadataLyricsImport.preview(song.lyrics)
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

    private fun cover(): AudioArtwork {
        val image = BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().run {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(31, 49, 85)
            fillRect(0, 0, 240, 240)
            color = Color(72, 101, 141)
            fillOval(-30, 135, 300, 200)
            color = Color(245, 220, 150)
            fillOval(143, 38, 48, 48)
            listOf(40 to 40, 90 to 75, 202 to 120, 58 to 124).forEach { (x, y) ->
                fillPolygon(intArrayOf(x, x + 3, x + 10, x + 3, x, x - 3, x - 10, x - 3),
                    intArrayOf(y - 10, y - 3, y, y + 3, y + 10, y + 3, y, y - 3), 8)
            }
            color = Color.WHITE
            drawLine(64, 173, 120, 209)
            drawLine(120, 209, 176, 173)
            drawRect(64, 169, 112, 55)
            dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        image.flush()
        return AudioArtwork(bytes, "image/png", description = "星光来信 · 教程示例封面")
    }
}
