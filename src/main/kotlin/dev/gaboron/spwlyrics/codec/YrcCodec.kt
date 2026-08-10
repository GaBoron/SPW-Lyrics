package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource

object YrcCodec : LyricCodec {
    private val linePattern = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val wordPattern = Regex("""\((\d+),(\d+),\d+\)(.*?)(?=\(\d+,\d+,\d+\)|$)""")

    override fun parse(raw: String, source: LyricsSource): LyricsDocument {
        val lines = mutableListOf<LyricLine>()
        val credits = mutableListOf<String>()
        raw.lineSequence().forEach { input ->
            val line = linePattern.matchEntire(input.trim()) ?: return@forEach
            val start = line.groupValues[1].toLong()
            val duration = line.groupValues[2].toLong()
            val body = line.groupValues[3]
            StructuredLyricsMetadata.creditText(body)?.let { credit ->
                credits += credit
                return@forEach
            }
            val words = wordPattern.findAll(body).map { match ->
                val wordStart = match.groupValues[1].toLong()
                val wordDuration = match.groupValues[2].toLong()
                LyricWord(wordStart, wordStart + wordDuration, match.groupValues[3])
            }.toList()
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text } else body
            if (text.isNotBlank()) lines += LyricLine(start, start + duration, text, words)
        }
        return LyricsDocument(
            source = source,
            format = LyricsFormat.YRC,
            lines = lines,
            metadata = if (credits.isEmpty()) emptyMap() else mapOf("credits" to credits.distinct()),
        )
    }

}
