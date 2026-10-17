package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Applies an offset to a copy; cached/source timelines always remain unchanged. */
object LyricsTimingOffset {
    fun shift(document: LyricsDocument, delayMs: Int): LyricsDocument {
        if (delayMs == 0) return document
        fun shiftTime(value: Long): Long =
            if (delayMs > 0 && value > Long.MAX_VALUE - delayMs) Long.MAX_VALUE
            else (value + delayMs).coerceAtLeast(0)
        return document.copy(lines = document.lines.map { line ->
            line.copy(
                startMs = line.startMs?.let(::shiftTime),
                endMs = line.endMs?.let(::shiftTime),
                words = line.words.map { word -> word.copy(startMs = shiftTime(word.startMs), endMs = shiftTime(word.endMs)) },
            )
        })
    }
}
