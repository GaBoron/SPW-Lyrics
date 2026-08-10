package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

class TtmlCodec : LyricCodec {
    override fun parse(raw: String, source: LyricsSource): LyricsDocument {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(raw)))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val primary = mutableListOf<LyricLine>()
        val translations = mutableListOf<LyricLine>()
        val romanizations = mutableListOf<LyricLine>()

        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val line = parseParagraph(paragraph) ?: continue
            when (paragraph.role()) {
                "translation", "x-translation" -> translations += line
                "romanization", "transliteration", "x-roman" -> romanizations += line
                else -> primary += line
            }
        }

        return LyricsTrackMerger.merge(
            LyricsDocument(source, LyricsFormat.TTML, primary),
            translations,
            romanizations,
        )
    }

    private fun parseParagraph(element: Element): LyricLine? {
        val lineStart = parseTime(element.attribute("begin"))
        val lineEnd = parseTime(element.attribute("end"))
        val words = mutableListOf<LyricWord>()
        val text = StringBuilder()
        var background = element.role() in setOf("x-bg", "background")
        var translation: String? = null
        var romanization: String? = null

        fun walk(node: Node) {
            when (node.nodeType) {
                Node.TEXT_NODE -> text.append(node.nodeValue)
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    if (child.localName == "br") {
                        text.append('\n')
                        return
                    }
                    when (child.role()) {
                        "translation", "x-translation" -> {
                            translation = child.textContent.orEmpty().trim().takeIf(String::isNotBlank)
                            return
                        }
                        "romanization", "transliteration", "x-roman" -> {
                            romanization = child.textContent.orEmpty().trim().takeIf(String::isNotBlank)
                            return
                        }
                    }
                    if (child.role() in setOf("x-bg", "background")) background = true
                    val childText = child.textContent.orEmpty()
                    val start = parseTime(child.attribute("begin"))
                    val end = parseTime(child.attribute("end"))
                    if (child.localName == "span" && start != null && end != null && childText.isNotEmpty()) {
                        words += LyricWord(start, end.coerceAtLeast(start), childText)
                        text.append(childText)
                    } else {
                        for (i in 0 until child.childNodes.length) walk(child.childNodes.item(i))
                    }
                }
            }
        }

        for (i in 0 until element.childNodes.length) walk(element.childNodes.item(i))
        val normalizedText = text.toString().replace(Regex("[\\t\\r ]+"), " ").trim()
        if (normalizedText.isEmpty()) return null
        return LyricLine(
            startMs = lineStart ?: words.firstOrNull()?.startMs,
            endMs = lineEnd ?: words.lastOrNull()?.endMs,
            text = normalizedText,
            words = words.sortedBy(LyricWord::startMs),
            translation = translation,
            romanization = romanization,
            background = background,
        )
    }

    private fun Element.attribute(localName: String): String =
        attributes?.let { attributes ->
            (0 until attributes.length)
                .map { attributes.item(it) }
                .firstOrNull { it.localName == localName || it.nodeName == localName }
                ?.nodeValue
        }.orEmpty()

    private fun Element.role(): String = attribute("role").lowercase()

    private fun parseTime(value: String): Long? {
        if (value.isBlank()) return null
        Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})(?:[.,](\\d+))?$").matchEntire(value)?.let { match ->
            val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            return match.groupValues[1].toLong() * 3_600_000 +
                match.groupValues[2].toLong() * 60_000 +
                match.groupValues[3].toLong() * 1_000 + fraction
        }
        Regex("^([0-9.]+)(ms|s|m|h)$").matchEntire(value)?.let { match ->
            val multiplier = when (match.groupValues[2]) {
                "ms" -> 1.0
                "s" -> 1_000.0
                "m" -> 60_000.0
                else -> 3_600_000.0
            }
            return (match.groupValues[1].toDouble() * multiplier).toLong()
        }
        return null
    }
}
