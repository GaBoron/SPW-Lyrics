package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.MetadataLyricsExport
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.domain.OnlineMetadataImport
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.AudioMetadataStore
import dev.gaboron.spwlyrics.storage.FileAudioMetadataStore
import java.nio.file.Path

internal data class BatchMetadataOptions(
    val fields: Set<AudioMetadataField> = setOf(AudioMetadataField.TITLE, AudioMetadataField.ARTIST, AudioMetadataField.ALBUM),
    val lyrics: Boolean = false,
    val cover: Boolean = false,
    val fillMissing: Boolean = true,
) {
    val hasSelection get() = fields.isNotEmpty() || lyrics || cover
}

/** Uses the same verified, recoverable tag transaction as the individual editor. */
internal class BatchMetadataWriter(
    private val resolver: LyricsResolver,
    private val store: AudioMetadataStore = FileAudioMetadataStore(),
    private val artwork: OnlineArtworkImport = OnlineArtworkImport(),
) {
    fun write(query: TrackQuery, resolved: ResolvedLyrics, options: BatchMetadataOptions): String {
        val original = store.read(Path.of(query.path))
        val fields = options.fields.filter { !options.fillMissing || original.metadata[it].isBlank() }.toSet()
        val cover = options.cover && (!options.fillMissing || original.metadata.cover == null)
        val lyrics = options.lyrics && (!options.fillMissing || original.metadata.lyrics.isBlank())
        if (fields.isEmpty() && !cover && !lyrics) return "已有所选数据，无需补充"

        val candidate = if (fields.isNotEmpty() || cover) resolver.metadata(resolved.candidate) else resolved.candidate
        var pending = OnlineMetadataImport.from(candidate).copy(selected = fields, fillMissing = options.fillMissing,
            coverSelected = cover, lyricsSelected = lyrics,
            lyricsText = if (lyrics) MetadataLyricsExport.encode(resolved.document, MetadataLyricsFormat.TTML) else null)
        if (cover) {
            pending = artwork.retry(pending)
            check(pending.cover != null) { pending.coverNotice ?: "此候选没有可写入的封面，请单独选择封面后重试。" }
        }
        val edited = pending.apply(original.metadata)
        if (edited == original.metadata) return "此来源没有可补充的所选数据"
        val saved = store.save(original, edited)
        return "已写入元数据。${saved.notice}"
    }
}
