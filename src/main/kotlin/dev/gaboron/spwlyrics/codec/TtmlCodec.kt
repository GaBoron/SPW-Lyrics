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
        val backgrounds = mutableListOf<LyricLine>()
        val translations = mutableListOf<LyricLine>()
        val romanizations = mutableListOf<LyricLine>()

        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val parsed = parseParagraph(paragraph)
            when (paragraph.role()) {
                "translation", "x-translation" -> parsed.main?.let(translations::add)
                "romanization", "transliteration", "x-roman" -> parsed.main?.let(romanizations::add)
                else -> {
                    parsed.main?.let(primary::add)
                    backgrounds += parsed.backgrounds
                }
            }
        }

        val merged = LyricsTrackMerger.merge(
            LyricsDocument(source, LyricsFormat.TTML, primary),
            translations,
            romanizations,
        )
        return merged.copy(
            lines = (merged.lines + backgrounds).sortedWith(
                compareBy<LyricLine> { it.startMs ?: Long.MAX_VALUE }.thenBy(LyricLine::background),
            ),
        )
    }

    private fun parseParagraph(element: Element): ParsedParagraph {
        val backgrounds = mutableListOf<LyricLine>()
        val paragraphIsBackground = element.role() in BACKGROUND_ROLES
        val line = parseLine(
            element = element,
            background = paragraphIsBackground,
            inheritedAgent = element.attribute("agent").takeIf(String::isNotBlank),
            backgrounds = backgrounds,
        )
        return if (paragraphIsBackground) {
            ParsedParagraph(main = null, backgrounds = listOfNotNull(line) + backgrounds)
        } else {
            ParsedParagraph(main = line, backgrounds = backgrounds)
        }
    }

    private fun parseLine(
        element: Element,
        background: Boolean,
        inheritedAgent: String?,
        backgrounds: MutableList<LyricLine>,
    ): LyricLine? {
        val lineStart = parseTime(element.attribute("begin"))
        val lineEnd = parseEnd(element, lineStart)
        val words = mutableListOf<LyricWord>()
        val text = StringBuilder()
        var translation: String? = null
        var romanization: String? = null
        val agent = element.attribute("agent").takeIf(String::isNotBlank) ?: inheritedAgent

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
                    if (!background && child.role() in BACKGROUND_ROLES) {
                        parseLine(child, background = true, inheritedAgent = agent, backgrounds = backgrounds)
                            ?.let(backgrounds::add)
                        return
                    }
                    val childText = child.textContent.orEmpty()
                    val start = parseTime(child.attribute("begin"))
                    val end = parseEnd(child, start)
                    val hasElementChildren = (0 until child.childNodes.length)
                        .any { child.childNodes.item(it).nodeType == Node.ELEMENT_NODE }
                    if (child.localName == "span" && !hasElementChildren && start != null && end != null && childText.isNotEmpty()) {
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
            agent = agent,
        )
    }

    private fun parseEnd(element: Element, start: Long?): Long? =
        parseTime(element.attribute("end"))
            ?: parseTime(element.attribute("dur"))?.let { duration -> start?.plus(duration) }

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
        Regex("^(\\d+):(\\d{1,2})(?:[.,](\\d+))?$").matchEntire(value)?.let { match ->
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            return match.groupValues[1].toLong() * 60_000 +
                match.groupValues[2].toLong() * 1_000 + fraction
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
        Regex("""^(\d+(?:[.,]\d+)?)$""").matchEntire(value)?.let { match ->
            return (match.groupValues[1].replace(',', '.').toDouble() * 1_000).toLong()
        }
        return null
    }

    private data class ParsedParagraph(
        val main: LyricLine?,
        val backgrounds: List<LyricLine>,
    )

    private companion object {
        val BACKGROUND_ROLES = setOf("x-bg", "background")
    }
}
