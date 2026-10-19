package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource

/** Reads LRC, enhanced LRC and SPL without confusing terminal/inline markers with repeated lines. */
internal object MetadataTimedLyricsReader {
    private val marker = Regex("""([\[<])(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?[\]>]""")
    private val attributes = Regex("""^\[([A-Za-z][\w-]*):(.*)]$""")

    fun parse(raw: String): LyricsDocument {
        val lines = mutableListOf<LyricLine>()
        val metadata = linkedMapOf<String, MutableList<String>>()
        var offset = 0L
        raw.lineSequence().forEach { input ->
            val text = input.trim().removePrefix("\uFEFF")
            if (text.isBlank()) return@forEach
            attributes.matchEntire(text)?.let { match ->
                val key = match.groupValues[1].lowercase()
                metadata.getOrPut(key) { mutableListOf() }.add(match.groupValues[2])
                if (key == "offset") offset = match.groupValues[2].toLongOrNull() ?: error("LRC offset 必须为整数。")
                return@forEach
            }
            val markers = marker.findAll(text).toList()
            if (markers.isEmpty()) {
                require(lines.isNotEmpty()) { "歌词缺少时间戳；仅支持 TTML、LRC、增强 LRC 和 SPL。" }
                lines += LyricLine(lines.last().startMs, lines.last().endMs, text)
                return@forEach
            }
            require(markers.first().range.first == 0 && markers.first().groupValues[1] == "[") { "每行歌词必须以方括号时间戳开始。" }
            val starts = markers.takeWhile { m -> m.groupValues[1] == "[" &&
                text.substring(0, m.range.first).let { marker.replace(it, "").isEmpty() } }
            val bodyStart = starts.last().range.last + 1
            if (bodyStart == text.length) {
                lines.lastOrNull()?.let { previous -> lines[lines.lastIndex] = previous.copy(endMs = starts.last().millis()) }
                return@forEach
            }
            starts.forEach { start ->
                val inner = markers.drop(starts.size)
                val endMarker = inner.lastOrNull()?.takeIf { it.range.last == text.lastIndex }
                val words = mutableListOf<LyricWord>()
                var cursor = bodyStart
                var wordStart = start.millis()
                val content = StringBuilder()
                inner.forEach { next ->
                    val piece = text.substring(cursor, next.range.first)
                    if (piece.isNotEmpty()) {
                        words += LyricWord(wordStart, next.millis().coerceAtLeast(wordStart), piece)
                        content.append(piece)
                    }
                    wordStart = next.millis()
                    cursor = next.range.last + 1
                }
                if (cursor < text.length) {
                    val piece = text.substring(cursor)
                    content.append(piece)
                    if (inner.isNotEmpty()) words += LyricWord(wordStart, wordStart, piece)
                }
                val startTime = start.millis()
                val endTime = endMarker?.millis()
                if (content.isNotBlank()) lines += LyricLine(startTime, endTime, content.toString(),
                    if (inner.isNotEmpty() && !(inner.size == 1 && endMarker != null)) words else emptyList())
            }
        }
        require(lines.isNotEmpty()) { "未找到有效的歌词正文。" }
        val sorted = TimedSecondaryLyrics.merge(lines).sortedBy { it.startMs }
        var groupStart: Long? = null
        var nextStart: Long? = null
        val completed = sorted.asReversed().map { line ->
            if (line.startMs != groupStart) {
                nextStart = groupStart
                groupStart = line.startMs
            }
            val end = line.endMs ?: nextStart
            line.copy(endMs = end, words = line.words.mapIndexed { index, word ->
                val wordEnd = line.words.getOrNull(index + 1)?.startMs ?: end
                if (word.endMs == word.startMs && wordEnd != null && wordEnd > word.startMs) word.copy(endMs = wordEnd)
                else word
            })
        }.asReversed()
        val document = LyricsDocument(LyricsSource.LOCAL, LyricsFormat.LRC, completed, metadata)
        return LyricsTimingOffset.shift(document, offset.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
    }

    private fun MatchResult.millis(): Long {
        require(groupValues[3].toInt() < 60) { "时间戳的秒数必须小于 60。" }
        return groupValues[2].toLong() * 60_000 + groupValues[3].toLong() * 1_000 +
            (groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0)
    }
}
