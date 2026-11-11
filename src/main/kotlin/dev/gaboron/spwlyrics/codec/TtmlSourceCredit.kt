package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Keeps required source credit at the end of both native TTML and derived playback text. */
internal object TtmlSourceCredit {
    fun append(document: LyricsDocument, name: String, url: String): LyricsDocument {
        val raw = document.rawTtml ?: return document
        val xml = TtmlXmlDocument.parse(raw)
        val body = xml.getElementsByTagNameNS("*", "body").item(0) ?: return document
        val start = document.lines.maxOfOrNull { it.effectiveEndMs() ?: it.startMs ?: 0 }?.plus(1)
        val line = LyricLine(startMs = start, text = "$name · $url", sourceCredit = true)
        val group = xml.createElementNS(TT, "div")
        val paragraph = xml.createElementNS(TT, "p")
        paragraph.setAttributeNS("http://www.w3.org/ns/ttml#metadata", "ttm:role", "x-credit")
        start?.let { paragraph.setAttribute("begin", "${it}ms") }
        paragraph.textContent = line.text
        group.appendChild(paragraph)
        body.appendChild(group)
        return document.copy(
            lines = document.lines + line,
            rawTtml = TtmlXmlDocument.write(xml),
            metadata = document.metadata + mapOf("source_name" to listOf(name), "source_url" to listOf(url)),
        )
    }

    private const val TT = "http://www.w3.org/ns/ttml"
}
