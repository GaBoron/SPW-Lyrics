package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.AudioArtwork
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory

/** Preserve all original artwork unless the user explicitly edits it. */
internal object AudioArtworkTags {
    fun read(tag: Tag?): List<AudioArtwork> = tag?.artworkList.orEmpty().map { image ->
        AudioArtwork(image.binaryData ?: byteArrayOf(), image.mimeType.orEmpty(), image.pictureType,
            image.description.orEmpty(), if (image.isLinked) image.imageUrl.orEmpty() else "")
    }

    fun write(tag: Tag, images: List<AudioArtwork>) {
        tag.deleteArtworkField()
        images.forEach { image ->
            tag.addField(ArtworkFactory.getNew().apply {
                binaryData = image.bytes
                mimeType = image.mimeType
                pictureType = image.pictureType
                description = image.description
                isLinked = image.linkedUrl.isNotBlank()
                imageUrl = image.linkedUrl
                if (!isLinked) check(setImageFromData()) { "封面图片无法解码，原歌曲未修改。" }
            })
        }
    }

    // Containers such as MP4 do not retain picture types or descriptions; payloads must be exact.
    fun matches(expected: List<AudioArtwork>, actual: List<AudioArtwork>) =
        expected.size == actual.size && expected.zip(actual).all { (a, b) -> a.samePayload(b) }
}
