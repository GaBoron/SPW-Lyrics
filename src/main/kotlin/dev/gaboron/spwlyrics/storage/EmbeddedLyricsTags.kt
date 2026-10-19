package dev.gaboron.spwlyrics.storage

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.ID3v1Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import java.util.Locale

/** One definition of embedded lyric aliases for playback and the metadata editor. */
internal object EmbeddedLyricsTags {
    private val aliases = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "TTMLLYRIC")

    fun read(tag: Tag): String {
        // ID3v1 has no lyric field and its generic field iterator is not implemented.
        if (tag is ID3v1Tag) return ""
        runCatching { tag.getAll(FieldKey.LYRICS).map { it.trimEnd('\u0000') }.firstOrNull(String::isNotBlank) }
            .getOrNull()?.let { return it }
        val fields = tag.fields
        while (fields.hasNext()) {
            val field = fields.next()
            val body = (field as? AbstractID3v2Frame)?.body
            val value = when {
                body is FrameBodyTXXX && isAlias(body.description) -> body.textWithoutTrailingNulls
                body is FrameBodyUSLT -> body.lyric.trimEnd('\u0000')
                field is TagTextField && isAlias(field.id) -> field.content
                else -> null
            }
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    fun write(tag: Tag, text: String) {
        if (text.isNotEmpty()) tag.setField(FieldKey.LYRICS, text)
        // Update existing aliases too, so another reader cannot resurrect the old lyrics.
        // Mutate only lyric fields, never the entire TXXX collection (which holds other tags).
        val fields = tag.fields
        while (fields.hasNext()) {
            val field = fields.next()
            val body = (field as? AbstractID3v2Frame)?.body
            when {
                body is FrameBodyTXXX && isAlias(body.description) -> {
                    body.textEncoding = if (tag is ID3v24Tag) 3 else 1
                    body.text = text
                }
                body is FrameBodyUSLT -> {
                    body.textEncoding = if (tag is ID3v24Tag) 3 else 1
                    body.lyric = text
                }
                field is TagTextField && isAlias(field.id) -> field.content = text
            }
        }
        if (text.isEmpty()) tag.deleteField(FieldKey.LYRICS)
    }

    private fun isAlias(id: String) = id.uppercase(Locale.ROOT) in aliases
}
