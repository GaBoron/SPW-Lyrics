package dev.gaboron.spwlyrics.domain

internal data class OnlineMetadataImport(
    val source: String,
    val fields: Map<AudioMetadataField, String>,
    val selected: Set<AudioMetadataField> = fields.keys,
    val notice: String? = null,
    val coverUrl: String? = null,
    val cover: AudioArtwork? = null,
    val coverSelected: Boolean = true,
    val coverNotice: String? = null,
    val lyricsText: String? = null,
    val lyricsDocument: LyricsDocument? = null,
    val lyricsMainAgent: String = "v1",
    val lyricsSelected: Boolean = true,
) {
    val hasSelection get() = fields.any { (field, value) -> field in selected && value.isNotBlank() } ||
        coverSelected && cover != null || lyricsSelected && !lyricsText.isNullOrBlank()

    /** Presets only change checkboxes; every selected, nonempty value replaces the draft. */
    fun selectFor(draft: AudioMetadata, onlyMissing: Boolean = true) = copy(
        selected = fields.filter { (field, value) -> value.isNotBlank() && (!onlyMissing || draft[field].isBlank()) }.keys,
        coverSelected = cover != null && (!onlyMissing || draft.cover == null),
        lyricsSelected = !lyricsText.isNullOrBlank() && (!onlyMissing || draft.lyrics.isBlank()),
    )

    fun apply(draft: AudioMetadata): AudioMetadata {
        val updated = draft.copy(fields = draft.fields + fields.filter { (field, value) ->
            field in selected && value.isNotBlank()
        }, lyrics = if (lyricsSelected && !lyricsText.isNullOrBlank()) lyricsText else draft.lyrics)
        return if (coverSelected && cover != null) updated.withCover(cover) else updated
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
