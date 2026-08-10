package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import kotlin.math.max

object SpwLyricsEncoder {
    const val VERSION = 1

    fun encode(document: LyricsDocument): String {
        if (document.lines.isEmpty()) return ""
        if (document.quality == LyricsQuality.PLAIN) {
            return document.lines.map(LyricLine::text).filter(String::isNotBlank).joinToString("\n")
        }

        val occupied = mutableSetOf<Long>()
        return buildList {
            document.lines.sortedWith(compareBy<LyricLine> { it.startMs ?: Long.MAX_VALUE }.thenBy { it.background })
                .forEach { line ->
                    val rawStart = line.startMs ?: return@forEach
                    var start = rawStart.coerceAtLeast(0)
                    while (!occupied.add(start)) start++
                    val main = if (document.quality == LyricsQuality.WORD_SYNCED && line.words.isNotEmpty()) {
                        encodeWords(line, start)
                    } else {
                        timestamp(start) + line.text + line.effectiveEndMs()?.takeIf { it > start }?.let(::timestamp).orEmpty()
                    }
                    add(main)

                    val secondary = line.translation?.takeIf(String::isNotBlank)
                        ?: line.romanization?.takeIf(String::isNotBlank)
                    secondary?.let { add(timestamp(start) + it) }
                }
        }.joinToString("\n")
    }

    private fun encodeWords(line: LyricLine, lineStart: Long): String = buildString {
        append(timestamp(lineStart))
        line.words.forEachIndexed { index, word ->
            if (index > 0 || word.startMs > lineStart) append(inlineTimestamp(max(word.startMs, lineStart)))
            append(word.text)
        }
        line.effectiveEndMs()?.takeIf { it > lineStart }?.let { append(timestamp(it)) }
    }

    fun timestamp(milliseconds: Long): String = format(milliseconds, '[', ']')

    fun inlineTimestamp(milliseconds: Long): String = format(milliseconds, '<', '>')

    private fun format(milliseconds: Long, open: Char, close: Char): String {
        val safe = milliseconds.coerceAtLeast(0)
        val minutes = safe / 60_000
        val seconds = safe % 60_000 / 1_000
        val millis = safe % 1_000
        return "%c%02d:%02d.%03d%c".format(open, minutes, seconds, millis, close)
    }
}
