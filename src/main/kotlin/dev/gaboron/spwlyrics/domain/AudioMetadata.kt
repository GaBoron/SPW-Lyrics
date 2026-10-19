package dev.gaboron.spwlyrics.domain

/** Editable song tags; file identity and persistence stay outside the domain model. */
internal enum class AudioMetadataField(val label: String) {
    TITLE("歌曲名"), ARTIST("歌手"), ALBUM("专辑"), ALBUM_ARTIST("专辑歌手"),
    YEAR("发行日期"), GENRE("流派"), TRACK("音轨号"), DISC("碟片号"),
    COMPOSER("作曲"), COMMENT("备注"),
}

internal data class AudioMetadata(
    val fields: Map<AudioMetadataField, String> = emptyMap(),
    val lyrics: String = "",
) {
    operator fun get(field: AudioMetadataField): String = fields[field].orEmpty()
}
