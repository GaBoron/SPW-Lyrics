package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import java.util.zip.InflaterInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object QrcCodec : LyricCodec {
    private val key = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(StandardCharsets.US_ASCII)
    private val linePattern = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val wordPattern = Regex("""(.*?)\((\d+),(\d+)\)""")

    fun decryptHex(content: String): String {
        val encrypted = HexFormat.of().parseHex(content.trim())
        require(encrypted.size % 8 == 0) { "Invalid QRC block length" }
        val decrypted = QqDesLike.decrypt(encrypted, key)
        return InflaterInputStream(ByteArrayInputStream(decrypted)).use { stream ->
            stream.readAllBytes().toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
        }
    }

    override fun parse(raw: String, source: LyricsSource): LyricsDocument {
        val content = extractLyricContent(raw)
        val lines = content.lineSequence().mapNotNull { input ->
            val line = linePattern.matchEntire(input.trim()) ?: return@mapNotNull null
            val lineStart = line.groupValues[1].toLong()
            val lineDuration = line.groupValues[2].toLong()
            val words = wordPattern.findAll(line.groupValues[3]).map { match ->
                val start = match.groupValues[2].toLong()
                val duration = match.groupValues[3].toLong()
                LyricWord(start, start + duration, match.groupValues[1])
            }.toList()
            LyricLine(lineStart, lineStart + lineDuration, words.joinToString("") { it.text }, words)
        }.toList()
        return if (lines.isNotEmpty()) {
            LyricsDocument(source, LyricsFormat.QRC, lines)
        } else {
            LrcCodec.parse(content, source).copy(format = LyricsFormat.QRC)
        }
    }

    private fun extractLyricContent(raw: String): String {
        if (!raw.contains("<?xml") && !raw.trimStart().startsWith("<Qrc")) return raw
        Regex(
            """\bLyricContent="(.*)"\s*/>\s*</LyricInfo>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(raw)?.let { match ->
            return unescapeXml(match.groupValues[1])
        }
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.isExpandEntityReferences = false
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))
            val nodes = document.getElementsByTagName("Lyric_1")
            (0 until nodes.length).firstNotNullOfOrNull { index ->
                nodes.item(index).attributes?.getNamedItem("LyricContent")?.nodeValue
            } ?: raw
        }.getOrDefault(raw)
    }

    private fun unescapeXml(value: String): String = value
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match -> match.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
