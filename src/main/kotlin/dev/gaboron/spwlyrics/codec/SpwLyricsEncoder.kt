package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import kotlin.math.max

object SpwLyricsEncoder {
    const val VERSION = 5

    fun encode(document: LyricsDocument): String {
        if (document.lines.isEmpty()) return ""
        val sourceLine = "歌词来源：${document.source.displayName}"
        if (document.quality == LyricsQuality.PLAIN) {
            return buildList {
                add(sourceLine)
                addAll(document.lines.map(LyricLine::text).filter(String::isNotBlank))
            }.joinToString("\n")
        }

        val firstLyricStart = document.lines.mapNotNull(LyricLine::startMs).minOrNull()?.coerceAtLeast(0)
        val sourceEnd = firstLyricStart?.let { if (it > 0) it else 1L }
        return buildList {
            add(timestamp(0) + sourceLine + sourceEnd?.let(::timestamp).orEmpty())
            document.lines.sortedWith(compareBy<LyricLine> { it.startMs ?: Long.MAX_VALUE }.thenBy { it.background })
                .forEach { line ->
                    val start = line.startMs?.coerceAtLeast(0) ?: return@forEach
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
        val segments = timedTextSegments(line)
        line.words.forEachIndexed { index, word ->
            if (index > 0 || word.startMs > lineStart) append(inlineTimestamp(max(word.startMs, lineStart)))
            append(segments[index])
        }
        line.effectiveEndMs()?.takeIf { it > lineStart }?.let { append(timestamp(it)) }
    }

    private fun timedTextSegments(line: LyricLine): List<String> {
        var cursor = 0
        val starts = line.words.map { word ->
            if (word.text.isEmpty()) return line.words.map(LyricWord::text)
            line.text.indexOf(word.text, cursor).also { index ->
                if (index < 0) return line.words.map(LyricWord::text)
                cursor = index + word.text.length
            }
        }
        return line.words.indices.map { index ->
            val start = if (index == 0) 0 else starts[index]
            val end = starts.getOrNull(index + 1) ?: line.text.length
            line.text.substring(start, end)
        }
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
