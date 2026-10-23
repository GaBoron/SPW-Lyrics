package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.*

/** Prefer untouched TTML over normalized rows, keeping performer/background/metadata information intact. */
internal object LrcShareLyricsCodec {
    fun parse(detail: JsonObject, full: JsonObject?, source: LyricsSource): LyricsDocument? {
        val raw = sequenceOf(detail, full).filterNotNull().flatMap { it.array("lyric_versions").asSequence() }
            .mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { version ->
                version.text("ttml_text")?.takeIf { version.text("format") == "ttml" && it.isNotBlank() }
            }
        if (raw != null) return TtmlCodec().parse(raw, source)
        val versions = (detail["lyric_lines"] as? JsonObject)?.array("versions").orEmpty().mapNotNull { it as? JsonObject }
        val original = versions.firstOrNull { it.text("kind") == "original" }
        if (original != null) {
            val lines = rows(original, timedWords = true)
            if (lines.isNotEmpty()) {
                val translation = versions.filter { it.text("kind") == "translation" }.flatMap { rows(it, false) }
                val romanization = versions.filter { it.text("kind") == "romanization" }.flatMap { rows(it, false) }
                return LyricsTrackMerger.merge(LyricsDocument(source, LyricsFormat.LRC, lines), translation, romanization)
            }
        }
        return (detail.text("lrc") ?: full?.text("lrc"))?.takeIf(String::isNotBlank)?.let { OnlineLyricsDecoder.parse(it, source) }
    }
    private fun rows(version: JsonObject, timedWords: Boolean): List<LyricLine> {
        val sorted = version.array("rows").mapNotNull { it as? JsonObject }.filter { it.number("time_ms") != null }.sortedBy { it.number("time_ms") }
        return sorted.mapIndexedNotNull { index, row ->
            val start = row.number("time_ms") ?: return@mapIndexedNotNull null
            val end = row.number("end_ms") ?: sorted.getOrNull(index + 1)?.number("time_ms") ?: start + 3000
            val text = row.text("text").orEmpty()
            val tokens = Regex("<(\\d{1,6})(?::(\\d{1,6}))?>").findAll(text).toList()
            val words = if (timedWords && tokens.isNotEmpty() && text.substring(0, tokens.first().range.first).isBlank()) tokens.mapIndexedNotNull { i, token ->
                val content = text.substring(token.range.last + 1, tokens.getOrNull(i + 1)?.range?.first ?: text.length)
                val from = start + token.groupValues[1].toLong()
                val to = token.groupValues[2].toLongOrNull()?.let { from + it } ?: tokens.getOrNull(i + 1)?.groupValues?.get(1)?.toLong()?.let { start + it } ?: end
                content.takeIf(String::isNotBlank)?.let { LyricWord(from, to, it) }
            } else emptyList()
            val plain = text.replace(Regex("<\\d{1,6}(?::\\d{1,6})?>"), "")
            val attrs = row["attrs"] as? JsonObject
            plain.takeIf(String::isNotBlank)?.let { LyricLine(start, end, it, words,
                background = attrs?.text("ttm:role") == "x-bg", agent = row.text("agent") ?: attrs?.text("ttm:agent")) }
        }
    }
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
