package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.LocalTtmlConversion
import java.nio.file.Files
import java.nio.file.Path
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT

/** Reads local content in the playback callback without involving online providers. */
internal class LocalTtmlLyricsLoader(
    private val conversion: LocalTtmlConversion = LocalTtmlConversion(),
) {
    fun load(audioPath: String): String? = runCatching {
        val audio = Path.of(audioPath)
        if (!Files.isRegularFile(audio)) return null
        val stem = audio.fileName.toString().substringBeforeLast('.', audio.fileName.toString())
        val lrc = audio.resolveSibling("$stem.lrc")
        if (Files.isRegularFile(lrc)) return readSidecar(lrc)?.let(conversion::convert)

        val embedded = readEmbedded(audio)
        if (embedded != null) return conversion.convert(embedded)

        readSidecar(audio.resolveSibling("$stem.ttml"))?.let(conversion::convert)
    }.getOrNull()

    private fun readSidecar(path: Path): String? = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_SIDECAR_BYTES) null
        else Files.readString(path)
    }.getOrNull()

    private fun readEmbedded(audio: Path): String? = runCatching {
        val tag = AudioFileIO.read(audio.toFile()).tag ?: return null
        runCatching { tag.getAll(FieldKey.LYRICS).firstOrNull(String::isNotBlank) }.getOrNull()
            ?: customLyrics(tag)
    }.getOrNull()

    private fun customLyrics(tag: Tag): String? {
        val fields = tag.fields
        while (fields.hasNext()) {
            val field = fields.next()
            val frame = field as? AbstractID3v2Frame
            val body = frame?.body
            val value = when {
                body is FrameBodyTXXX && body.description.uppercase() in LYRIC_TAGS -> body.text
                body is FrameBodyUSLT -> body.lyric
                field.id.uppercase() in LYRIC_TAGS && field is TagTextField -> field.content
                else -> null
            }
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private companion object {
        const val MAX_SIDECAR_BYTES = 4_000_000L
        val LYRIC_TAGS = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "TTMLLYRIC")
    }
}
