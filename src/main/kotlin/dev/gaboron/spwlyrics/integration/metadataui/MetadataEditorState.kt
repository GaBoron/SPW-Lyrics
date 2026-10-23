package dev.gaboron.spwlyrics.integration.metadataui

import dev.gaboron.spwlyrics.domain.AudioMetadata
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.codec.TtmlVocalParts
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import dev.gaboron.spwlyrics.storage.AudioMetadataSnapshot

internal data class MetadataEditorState(
    val loaded: AudioMetadataSnapshot? = null,
    val onlineImport: dev.gaboron.spwlyrics.domain.OnlineMetadataImport? = null,
    val draft: AudioMetadata = AudioMetadata(),
    val format: MetadataLyricsFormat = MetadataLyricsFormat.TTML,
    val sourceDocument: LyricsDocument? = null,
    val preview: LyricsDocument? = null,
    val structured: EditableLyrics? = null,
    val structuredError: String? = null,
    val lineEdits: Map<Int, EditableLyricLine> = emptyMap(),
    val selectedLineId: Int? = null,
    val selectedLineIds: Set<Int> = emptySet(),
    val selectionAnchor: Int? = null,
    val previewError: String? = null,
    val validating: Boolean = false,
    val busy: Boolean = false,
    val status: String = "读取当前歌曲，或打开本地歌曲文件。",
    val error: Boolean = false,
    val pendingDiscard: (() -> Unit)? = null,
) {
    val dirty get() = loaded != null && (draft != loaded.metadata || lineEdits.isNotEmpty())
    val lyricsChanged get() = loaded != null && draft.lyrics != loaded.metadata.lyrics
    val canEditRows get() = loaded != null && !busy && structuredError == null && (structured != null || draft.lyrics.isBlank())
    val canSave get() = dirty && !busy && pendingDiscard == null &&
        (!(lyricsChanged || lineEdits.isNotEmpty()) || draft.lyrics.isBlank() || !validating && previewError == null)
    fun line(id: Int): EditableLyricLine? = lineEdits[id] ?: structured?.lines?.firstOrNull { it.id == id }
    fun vocalPart(line: EditableLyricLine): LyricVocalPart = TtmlVocalParts.part(line, structured?.mainAgent ?: "v1")
    val previewMainAgent get() = if (lineEdits.values.any { it.vocalPart != null }) "v1" else structured?.mainAgent ?: "v1"
    fun lineText(id: Int): String {
        val edited = line(id) ?: return ""
        val original = structured?.lines?.firstOrNull { it.id == id } ?: return edited.text
        if (original.words.isEmpty() || edited.words.isEmpty()) return edited.text
        var cursor = 0
        return buildString {
            original.words.forEachIndexed { index, word ->
                val position = original.text.indexOf(word.text, cursor)
                if (position < cursor || word.text.isEmpty()) return edited.words.joinToString("") { it.text }
                append(original.text.substring(cursor, position)); append(edited.words[index].text)
                cursor = position + word.text.length
            }
            append(original.text.substring(cursor))
        }
    }
}
