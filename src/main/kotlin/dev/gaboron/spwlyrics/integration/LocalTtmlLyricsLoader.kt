package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.LocalLyricsConversion
import dev.gaboron.spwlyrics.domain.LyricsDocument
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import org.jaudiotagger.audio.AudioFileIO
import dev.gaboron.spwlyrics.storage.EmbeddedLyricsTags

/** Reads local content in the playback callback without involving online providers. */
internal class LocalTtmlLyricsLoader(
    private val conversion: LocalLyricsConversion = LocalLyricsConversion(),
) {
    private data class Stamp(val size: Long, val modified: FileTime)
    private data class Snapshot(val stamps: List<Stamp?>, val document: LyricsDocument?)
    private val snapshots = object : LinkedHashMap<Path, Snapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, Snapshot>?): Boolean = size > 64
    }

    fun load(audioPath: String): LyricsDocument? = runCatching {
        val audio = Path.of(audioPath)
        val stem = audio.fileName.toString().substringBeforeLast('.', audio.fileName.toString())
        val lrc = audio.resolveSibling("$stem.lrc")
        val ttml = audio.resolveSibling("$stem.ttml")
        val stamps = listOf(audio, lrc, ttml).map(::stamp)
        if (stamps.first() == null) return null
        synchronized(snapshots) { snapshots[audio]?.takeIf { it.stamps == stamps }?.let { return it.document } }
        val document = when {
            stamps[1] != null -> readSidecar(lrc)?.let(conversion::parse)
            else -> {
                val embedded = readEmbedded(audio)
                if (embedded != null) conversion.parse(embedded) else readSidecar(ttml)?.let(conversion::parse)
            }
        }
        synchronized(snapshots) { snapshots[audio] = Snapshot(stamps, document) }
        document
    }.getOrNull()

    private fun stamp(path: Path): Stamp? = runCatching {
        Files.readAttributes(path, BasicFileAttributes::class.java).takeIf { it.isRegularFile }
            ?.let { Stamp(it.size(), it.lastModifiedTime()) }
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
