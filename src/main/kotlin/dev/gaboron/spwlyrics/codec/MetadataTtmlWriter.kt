package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsDocument
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Builds TTML for non-TTML sources; existing XML is formatted without rebuilding its lyric nodes. */
internal object MetadataTtmlWriter {
    private const val TT = "http://www.w3.org/ns/ttml"
    private const val TTM = "http://www.w3.org/ns/ttml#metadata"
    private const val AMLL = "http://www.example.com/ns/amll"

    fun write(source: LyricsDocument): String {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        fun Element.child(name: String, namespace: String = TT): Element = xml.createElementNS(namespace, name).also(::appendChild)
        fun Element.time(name: String, value: Long?) {
            value?.let { setAttribute(name, LyricEditTime.format(it.coerceAtLeast(0))) }
        }
        val root = xml.createElementNS(TT, "tt").also(xml::appendChild)
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ttm", TTM)
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:amll", AMLL)
        root.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve")
        val metadata = root.child("head").child("metadata")
        source.metadata.forEach { (key, values) -> values.forEach { value ->
            metadata.child("amll:meta", AMLL).apply { setAttribute("key", key); setAttribute("value", value) }
        } }
        val agents = source.lines.mapNotNull { it.agent }.distinct().mapIndexed { index, value -> value to "v${index + 1}" }.toMap()
        agents.values.forEach { id -> metadata.child("ttm:agent", TTM).apply {
            setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", id); setAttribute("type", "person")
        } }
        val body = root.child("body").child("div")
        source.lines.forEach { line ->
            body.child("p").apply {
                time("begin", line.startMs)
                time("end", line.effectiveEndMs())
                if (line.background) setAttributeNS(TTM, "ttm:role", "x-bg")
                agents[line.agent]?.let { setAttributeNS(TTM, "ttm:agent", it) }
                var cursor = 0
                line.words.forEach { word ->
                    val index = line.text.indexOf(word.text, cursor)
                    require(index >= cursor && word.text.isNotEmpty()) { "逐字文本与歌词正文不一致，无法转换 TTML。" }
                    appendChild(xml.createTextNode(line.text.substring(cursor, index)))
                    child("span").apply { time("begin", word.startMs); time("end", word.endMs); textContent = word.text }
                    cursor = index + word.text.length
                }
                appendChild(xml.createTextNode(line.text.substring(cursor)))
                listOf("x-translation" to line.translation, "x-roman" to line.romanization).forEach { (role, text) ->
                    if (!text.isNullOrBlank()) child("span").apply { setAttributeNS(TTM, "ttm:role", role); textContent = text }
                }
            }
        }
        return TtmlXmlDocument.write(xml)
    }
}
