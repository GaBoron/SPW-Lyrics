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
import dev.gaboron.spwlyrics.codec.LrcCodec
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource

/** Reads local content in the playback callback without involving online providers. */
internal class LocalTtmlLyricsLoader(
    private val conversion: LocalLyricsConversion = LocalLyricsConversion(),
    private val readEmbedded: (Path) -> String? = ::readEmbeddedLyrics,
) {
    private data class Stamp(val size: Long, val modified: FileTime)
    private data class Snapshot(val stamps: List<Stamp?>, val document: LyricsDocument?, val original: LyricsDocument?)
    private data class Key(val audio: Path, val embeddedOnly: Boolean)
    private val snapshots = object : LinkedHashMap<Key, Snapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Snapshot>?): Boolean = size > 64
    }

    fun load(audioPath: String): LyricsDocument? = load(audioPath, embeddedOnly = false)

    fun loadEmbedded(audioPath: String): LyricsDocument? = load(audioPath, embeddedOnly = true)

    /** Public readers only inspect content already correlated with a playback callback. */
    fun original(audioPath: String, embeddedOnly: Boolean): LyricsDocument? {
        val audio = localMediaPath(audioPath) ?: return null
        return synchronized(snapshots) { snapshots[Key(audio, embeddedOnly)]?.original }
    }

    fun invalidate(audioPath: String) {
        val audio = localMediaPath(audioPath) ?: return
        synchronized(snapshots) { snapshots.keys.removeAll { it.audio == audio } }
    }

    private fun load(audioPath: String, embeddedOnly: Boolean): LyricsDocument? = runCatching {
        val audio = localMediaPath(audioPath) ?: return null
        val key = Key(audio, embeddedOnly)
        val stem = audio.fileName.toString().substringBeforeLast('.', audio.fileName.toString())
        val lrc = audio.resolveSibling("$stem.lrc")
        val ttml = audio.resolveSibling("$stem.ttml")
        val stamps = listOf(audio, lrc, ttml).map(::stamp)
        if (stamps.first() == null) {
            synchronized(snapshots) { snapshots.remove(key) }
            return null
        }
        synchronized(snapshots) { snapshots[key]?.takeIf { it.stamps == stamps }?.let { return it.document } }
        val raw = when {
            embeddedOnly -> readEmbedded(audio)
            stamps[1] != null -> readSidecar(lrc)
            else -> {
                val embedded = readEmbedded(audio)
                embedded ?: readSidecar(ttml)
            }
        }
        val document = if (embeddedOnly) MetadataLyricsImport.preview(raw.orEmpty())
            ?: LyricsDocument(LyricsSource.LOCAL, LyricsFormat.PLAIN, emptyList()) else raw?.let(conversion::parse)
        val original = document ?: raw?.let { text -> runCatching {
            if (text.trimStart().startsWith('<')) MetadataLyricsImport.preview(text)
            else LrcCodec.parse(text, LyricsSource.LOCAL)
        }.getOrNull() }
        synchronized(snapshots) { snapshots[key] = Snapshot(stamps, document, original) }
        document
    }.onFailure { invalidate(audioPath) }.getOrNull()

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
