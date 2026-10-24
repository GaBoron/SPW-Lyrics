package dev.gaboron.spwlyrics.domain

internal data class OnlineMetadataImport(
    val source: String,
    val fields: Map<AudioMetadataField, String>,
    val selected: Set<AudioMetadataField> = fields.keys,
    val fillMissing: Boolean = true,
    val notice: String? = null,
    val coverUrl: String? = null,
    val cover: AudioArtwork? = null,
    val coverSelected: Boolean = true,
    val coverNotice: String? = null,
    val lyricsText: String? = null,
    val lyricsDocument: LyricsDocument? = null,
    val lyricsSelected: Boolean = true,
) {
    fun apply(draft: AudioMetadata): AudioMetadata {
        val updated = draft.copy(fields = draft.fields + fields.filter { (field, value) ->
            field in selected && value.isNotBlank() && (!fillMissing || draft[field].isBlank())
        }, lyrics = if (lyricsSelected && lyricsText != null && (!fillMissing || draft.lyrics.isBlank())) lyricsText else draft.lyrics)
        return if (coverSelected && cover != null && (!fillMissing || draft.cover == null)) updated.withCover(cover) else updated
    }
    companion object {
        fun from(candidate: LyricsCandidate): OnlineMetadataImport {
            val values = mapOf("title" to listOf(candidate.title), "artist" to candidate.artists, "album" to listOf(candidate.album)) + candidate.metadata
            return OnlineMetadataImport(candidate.source.displayName, AudioMetadataField.entries.mapNotNull { field ->
                values[field.onlineKey]?.map(String::trim)?.filter(String::isNotBlank)?.distinct()?.joinToString("\n")
                    ?.takeIf(String::isNotBlank)?.let { field to it }
            }.toMap(), notice = candidate.metadataNotice,
                coverUrl = candidate.metadata["cover_url"]?.firstOrNull()?.takeIf(String::isNotBlank))
        }
    }
}
