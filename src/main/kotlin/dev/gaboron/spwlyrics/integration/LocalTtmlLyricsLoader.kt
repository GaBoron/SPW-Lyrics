package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.LocalLyricsConversion
import dev.gaboron.spwlyrics.domain.LyricsDocument
import java.nio.file.Files
import java.nio.file.Path
import org.jaudiotagger.audio.AudioFileIO
import dev.gaboron.spwlyrics.storage.EmbeddedLyricsTags

/** Reads local content in the playback callback without involving online providers. */
internal class LocalTtmlLyricsLoader(
    private val conversion: LocalLyricsConversion = LocalLyricsConversion(),
) {
    fun load(audioPath: String): LyricsDocument? = runCatching {
        val audio = Path.of(audioPath)
        if (!Files.isRegularFile(audio)) return null
        val stem = audio.fileName.toString().substringBeforeLast('.', audio.fileName.toString())
        val lrc = audio.resolveSibling("$stem.lrc")
        if (Files.isRegularFile(lrc)) return readSidecar(lrc)?.let(conversion::parse)

        val embedded = readEmbedded(audio)
        if (embedded != null) return conversion.parse(embedded)

        readSidecar(audio.resolveSibling("$stem.ttml"))?.let(conversion::parse)
    }.getOrNull()

    private fun readSidecar(path: Path): String? = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_SIDECAR_BYTES) null
        else Files.readString(path)
    }.getOrNull()

    private fun readEmbedded(audio: Path): String? = runCatching {
        val tag = AudioFileIO.read(audio.toFile()).tag ?: return null
        EmbeddedLyricsTags.read(tag).takeIf(String::isNotBlank)
    }.getOrNull()

    private companion object {
        const val MAX_SIDECAR_BYTES = 4_000_000L
    }
}
