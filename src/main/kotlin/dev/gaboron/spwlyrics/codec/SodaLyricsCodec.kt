package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.*

/** Numeric sentence times and relative word offsets used by Qishui's SEO lyric response. */
internal object SodaLyricsCodec {
    fun parse(raw: String, source: LyricsSource): LyricsDocument {
        val lines = raw.lineSequence().mapNotNull { input ->
            val match = Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(input.trim()) ?: return@mapNotNull null
            val start = match.groupValues[1].toLong()
            val end = start + match.groupValues[2].toLong()
            val content = match.groupValues[3]
            val words = Regex("<(\\d+),(\\d+),\\d+>([^<]*)").findAll(content).mapNotNull {
                val text = it.groupValues[3].takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val from = start + it.groupValues[1].toLong()
                LyricWord(from, from + it.groupValues[2].toLong(), text)
            }.toList()
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text } else content.replace(Regex("<\\d+,\\d+,\\d+>"), "")
            text.takeIf(String::isNotBlank)?.let { LyricLine(start, maxOf(end, words.maxOfOrNull { it.endMs } ?: end), it, words) }
        }.toList()
        return LyricsDocument(source, LyricsFormat.LRC, lines)
    }
}
