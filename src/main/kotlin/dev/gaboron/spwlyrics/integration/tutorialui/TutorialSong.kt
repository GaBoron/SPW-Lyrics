package dev.gaboron.spwlyrics.integration.tutorialui

import dev.gaboron.spwlyrics.codec.MetadataLyricsImport
import dev.gaboron.spwlyrics.domain.*
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

/** Shared fictional song; no audio file, provider request or saved playback choice is involved. */
internal object TutorialSong {
    val metadata = AudioMetadata(
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
    val path = Path.of("教程示例", "星光来信.flac")
    val document = checkNotNull(MetadataLyricsImport.preview(metadata.lyrics))
    val query = TrackQuery("星光来信", listOf("示例歌手"), "夜空练习册", path = path.toString(), durationMs = 24000)
    val candidate = LyricsCandidate(LyricsSource.AMLL, "tutorial-starlight", query.title, query.artists,
        query.album, query.durationMs, document.quality, metadata = mapOf("date" to listOf("2026"), "language" to listOf("中文")))

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
