package dev.gaboron.spwlyrics.domain

/** Editable song tags; file identity and persistence stay outside the domain model. */
internal enum class AudioMetadataField(val label: String, val onlineKey: String) {
    TITLE("歌曲名", "title"), ARTIST("歌手", "artist"), ALBUM("专辑", "album"), ALBUM_ARTIST("专辑歌手", "album_artist"),
    YEAR("发行日期", "date"), GENRE("流派", "genre"), TRACK("音轨号", "track_number"), DISC("碟片号", "disc_number"),
    COMPOSER("作曲", "composer"), LYRICIST("作词", "lyricist"), LANGUAGE("语言", "language"), COPYRIGHT("版权", "copyright"), COMMENT("备注", "comment"),
}

internal data class AudioMetadata(
    val fields: Map<AudioMetadataField, String> = emptyMap(),
    val lyrics: String = "",
    val artwork: List<AudioArtwork> = emptyList(),
) {
    operator fun get(field: AudioMetadataField): String = fields[field].orEmpty()
    val cover: AudioArtwork? get() = artwork.firstOrNull { it.pictureType == 3 } ?: artwork.firstOrNull()

    fun withCover(image: AudioArtwork): AudioMetadata {
        // Untyped images (such as MP4 covr) use their first image as the cover.
        val replaced = cover?.takeIf { it.pictureType <= 0 }
        return copy(artwork = listOf(image) + artwork.filter { it.pictureType != 3 && it !== replaced })
    }
}
