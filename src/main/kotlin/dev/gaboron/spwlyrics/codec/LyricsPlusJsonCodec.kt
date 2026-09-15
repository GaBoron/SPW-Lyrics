package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Decodes the normalized JSON returned by LyricsPlus v2 endpoints. */
class LyricsPlusJsonCodec : LyricCodec {
    override fun parse(raw: String, source: LyricsSource): LyricsDocument = decode(raw, source).document

    fun decode(raw: String, source: LyricsSource): LyricsPlusPayload {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return LyricsPlusPayload(emptyDocument(source))
        val metadata = root.objectValue("metadata")
        val upstreamSource = metadata?.stringValue("source") ?: metadata?.stringValue("provider")
        val lineType = root.stringValue("type").equals("line", ignoreCase = true)
        val entries = root.arrayValue("lyrics")
            ?: root.objectValue("data")?.arrayValue("lyrics")
            ?: (root["data"] as? JsonArray)
            ?: JsonArray(emptyList())
        val lines = entries.flatMap { element ->
            parseEntry(element as? JsonObject ?: return@flatMap emptyList(), lineType)
        }.sortedWith(compareBy<LyricLine> { it.startMs ?: Long.MAX_VALUE }.thenBy(LyricLine::background))
        val documentMetadata = upstreamSource?.takeIf(String::isNotBlank)
            ?.let { mapOf("upstreamSource" to listOf(it)) }
            .orEmpty()
        return LyricsPlusPayload(
            document = LyricsDocument(source, LyricsFormat.TTML, lines, documentMetadata),
            upstreamSource = upstreamSource,
            title = metadata?.stringValue("title"),
            artist = metadata?.stringValue("artist"),
            album = metadata?.stringValue("album"),
            isrc = metadata?.stringValue("isrc"),
        )
    }

    private fun parseEntry(entry: JsonObject, lineType: Boolean): List<LyricLine> {
        val start = entry.timeValue("time")
        val end = entry.endValue(start)
        val agent = entry.objectValue("element")?.stringValue("singer")
        val translation = entry.textValue("translation")
        val romanization = entry.textValue("transliteration")
        val syllables = if (lineType) null else entry.arrayValue("syllabus") ?: entry.arrayValue("words")
        val parsedWords = syllables.orEmpty().mapNotNull(::parseWord)
        val mainWords = parsedWords.filterNot(ParsedWord::background).map(ParsedWord::word)
        val backgroundWords = parsedWords.filter(ParsedWord::background).map(ParsedWord::word)
        val rawText = entry.stringValue("text").orEmpty().trim()
        val mainText = rawText.ifBlank { mainWords.joinToString("") { it.text }.trim() }
        val main = mainText.takeIf(String::isNotBlank)?.let {
            LyricLine(
                startMs = start ?: mainWords.firstOrNull()?.startMs,
                endMs = end ?: mainWords.lastOrNull()?.endMs,
                text = it,
                words = mainWords,
                translation = translation,
                romanization = romanization,
                agent = agent,
            )
        }
        val background = backgroundWords.takeIf(List<LyricWord>::isNotEmpty)?.let { words ->
            LyricLine(
                startMs = words.first().startMs,
                endMs = words.last().endMs,
                text = words.joinToString("") { it.text }.trim(),
                words = words,
                background = true,
                agent = agent,
            )
        }?.takeIf { it.text.isNotBlank() }
        return listOfNotNull(main, background)
    }

    private fun parseWord(element: JsonElement): ParsedWord? {
        val value = element as? JsonObject ?: return null
        val start = value.timeValue("time") ?: return null
        val end = value.endValue(start) ?: return null
        val text = value.stringValue("text") ?: return null
        if (text.isEmpty()) return null
        return ParsedWord(LyricWord(start, end.coerceAtLeast(start), text), value.booleanValue("isBackground"))
    }

    private fun JsonObject.endValue(start: Long?): Long? =
        timeValue("endTime") ?: timeValue("end") ?: timeValue("duration")?.let { duration -> start?.plus(duration) }

    private fun JsonObject.timeValue(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.longOrNull?.let { return it }
        return primitive.doubleOrNull?.let { (it * 1_000.0).toLong() }
    }

    private fun JsonObject.textValue(key: String): String? = when (val value = this[key]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.stringValue("text")
        else -> null
    }?.trim()?.takeIf(String::isNotBlank)

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.booleanValue(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arrayValue(key: String): JsonArray? = this[key] as? JsonArray

    private fun emptyDocument(source: LyricsSource) = LyricsDocument(source, LyricsFormat.TTML, emptyList())

    private data class ParsedWord(val word: LyricWord, val background: Boolean)

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

data class LyricsPlusPayload(
    val document: LyricsDocument,
    val upstreamSource: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isrc: String? = null,
)
