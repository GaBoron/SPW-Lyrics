package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource

object LrcCodec : LyricCodec {
    private val lineTime = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val inlineTime = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val metadata = Regex("""^\[([A-Za-z][\w-]*):(.*)]$""")

    override fun parse(raw: String, source: LyricsSource): LyricsDocument {
        var offset = 0L
        val attributes = linkedMapOf<String, MutableList<String>>()
        val parsed = mutableListOf<LyricLine>()

        raw.removePrefix("\uFEFF").lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            metadata.matchEntire(line)?.let { match ->
                val key = match.groupValues[1].lowercase()
                val value = match.groupValues[2].trim()
                if (key == "offset") offset = value.toLongOrNull() ?: 0
                attributes.getOrPut(key) { mutableListOf() }.add(value)
                return@forEach
            }

            StructuredLyricsMetadata.creditText(line)?.let { credit ->
                attributes.getOrPut("credits") { mutableListOf() }.add(credit)
                return@forEach
            }

            val timestamps = lineTime.findAll(line).toList()
            if (timestamps.isEmpty()) {
                if (line.isNotBlank() && !line.startsWith('[')) parsed += LyricLine(text = line)
                return@forEach
            }
            val content = line.substring(timestamps.last().range.last + 1)
            StructuredLyricsMetadata.creditText(content)?.let { credit ->
                attributes.getOrPut("credits") { mutableListOf() }.add(credit)
                return@forEach
            }
            timestamps.forEach { timestamp ->
                val start = timestamp.toMillis() + offset
                val words = parseInlineWords(content, start, offset)
                val text = inlineTime.replace(content, "")
                parsed += LyricLine(
                    startMs = start.coerceAtLeast(0),
                    text = text,
                    words = words,
                )
            }
        }

        val sorted = parsed.sortedBy { it.startMs ?: Long.MAX_VALUE }
        val completed = sorted.mapIndexed { index, line ->
            if (line.startMs == null || line.endMs != null) line else line.copy(
                endMs = line.words.maxOfOrNull(LyricWord::endMs)
                    ?: sorted.drop(index + 1).firstNotNullOfOrNull(LyricLine::startMs),
            )
        }
        return LyricsDocument(source, LyricsFormat.LRC, completed, attributes)
    }

    private fun parseInlineWords(content: String, lineStart: Long, offset: Long): List<LyricWord> {
        val markers = inlineTime.findAll(content).toList()
        if (markers.isEmpty()) return emptyList()
        val pieces = mutableListOf<Pair<Long, String>>()
        var cursor = 0
        var currentStart = lineStart
        markers.forEach { marker ->
            if (marker.range.first > cursor) {
                val text = content.substring(cursor, marker.range.first)
                if (text.isNotEmpty()) pieces += currentStart to text
            }
            currentStart = marker.toMillis() + offset
            cursor = marker.range.last + 1
        }
        if (cursor < content.length) pieces += currentStart to content.substring(cursor)
        return pieces.filter { it.second.isNotEmpty() }.mapIndexed { index, (start, text) ->
            val end = pieces.getOrNull(index + 1)?.first ?: start + 300
            LyricWord(start.coerceAtLeast(0), end.coerceAtLeast(start), text)
        }
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fraction = groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        return minutes * 60_000 + seconds * 1_000 + fraction
    }
}
