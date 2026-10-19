package dev.gaboron.spwlyrics.domain

internal enum class LyricVocalPart { MAIN, DUET }

internal data class EditableLyricWord(
    val path: List<Int>, val start: String, val end: String, val text: String,
    val hasMarkup: Boolean = false,
)

internal data class EditableLyricLine(
    val id: Int, val path: List<Int>, val start: String, val end: String,
    val text: String, val agent: String, val background: Boolean,
    val translation: String, val romanization: String,
    val words: List<EditableLyricWord>,
    val vocalPart: LyricVocalPart? = null,
)

/** Keeps the original XML so focused edits retain ruby, extensions and metadata. */
internal data class EditableLyrics(val raw: String, val lines: List<EditableLyricLine>, val mainAgent: String = "v1")
