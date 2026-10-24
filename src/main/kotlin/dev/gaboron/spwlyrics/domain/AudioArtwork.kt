package dev.gaboron.spwlyrics.domain

/** Embedded image payloads use content equality so re-reading a file does not dirty the draft. */
internal class AudioArtwork(
    val bytes: ByteArray,
    val mimeType: String,
    val pictureType: Int = 3,
    val description: String = "",
    val linkedUrl: String = "",
) {
    fun samePayload(other: AudioArtwork) = bytes.contentEquals(other.bytes) && linkedUrl == other.linkedUrl
    override fun equals(other: Any?): Boolean = other is AudioArtwork && samePayload(other) &&
        mimeType == other.mimeType && pictureType == other.pictureType && description == other.description
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + listOf(mimeType, pictureType, description, linkedUrl).hashCode()
}
