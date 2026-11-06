package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.LocalLyricsConversion
import dev.gaboron.spwlyrics.domain.LyricsDocument
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import org.jaudiotagger.audio.AudioFileIO
import dev.gaboron.spwlyrics.storage.EmbeddedLyricsTags
import dev.gaboron.spwlyrics.codec.MetadataLyricsImport
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource

/** Reads local content in the playback callback without involving online providers. */
internal class LocalTtmlLyricsLoader(
    private val conversion: LocalLyricsConversion = LocalLyricsConversion(),
    private val readEmbedded: (Path) -> String? = ::readEmbeddedLyrics,
) {
    private data class Stamp(val size: Long, val modified: FileTime)
    private data class Snapshot(val stamps: List<Stamp?>, val document: LyricsDocument?)
    private data class Key(val audio: Path, val embeddedOnly: Boolean)
    private val snapshots = object : LinkedHashMap<Key, Snapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Snapshot>?): Boolean = size > 64
    }

    fun load(audioPath: String): LyricsDocument? = load(audioPath, embeddedOnly = false)

    fun loadEmbedded(audioPath: String): LyricsDocument? = load(audioPath, embeddedOnly = true)

    fun invalidate(audioPath: String) {
        val audio = Path.of(audioPath)
        synchronized(snapshots) { snapshots.keys.removeAll { it.audio == audio } }
    }

    private fun load(audioPath: String, embeddedOnly: Boolean): LyricsDocument? = runCatching {
        val audio = Path.of(audioPath)
        val key = Key(audio, embeddedOnly)
        val stem = audio.fileName.toString().substringBeforeLast('.', audio.fileName.toString())
        val lrc = audio.resolveSibling("$stem.lrc")
        val ttml = audio.resolveSibling("$stem.ttml")
        val stamps = listOf(audio, lrc, ttml).map(::stamp)
        if (stamps.first() == null) return null
        synchronized(snapshots) { snapshots[key]?.takeIf { it.stamps == stamps }?.let { return it.document } }
        val document = when {
            embeddedOnly -> MetadataLyricsImport.preview(readEmbedded(audio).orEmpty())
                ?: LyricsDocument(LyricsSource.LOCAL, LyricsFormat.PLAIN, emptyList())
            stamps[1] != null -> readSidecar(lrc)?.let(conversion::parse)
            else -> {
                val embedded = readEmbedded(audio)
                if (embedded != null) conversion.parse(embedded) else readSidecar(ttml)?.let(conversion::parse)
            }
        }
        synchronized(snapshots) { snapshots[key] = Snapshot(stamps, document) }
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

    private companion object {
        const val MAX_SIDECAR_BYTES = 4_000_000L
    }
}

private fun readEmbeddedLyrics(audio: Path): String? = runCatching {
    val tag = AudioFileIO.read(audio.toFile()).tag ?: return null
    EmbeddedLyricsTags.read(tag).takeIf(String::isNotBlank)
}.getOrNull()
