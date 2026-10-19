package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.LyricVocalPart
import org.w3c.dom.Element
import javax.xml.XMLConstants

/** Convert the editor's left/right intent into stable AMLL vocal definitions. */
internal object TtmlVocalParts {
    private const val TT = "http://www.w3.org/ns/ttml"
    private const val TTM = "http://www.w3.org/ns/ttml#metadata"

    fun mainAgent(root: Element): String {
        val agents = root.getElementsByTagNameNS(TTM, "agent")
        return (0 until agents.length).map { agents.item(it) as Element }
            .firstOrNull { it.getAttribute("type") == "person" }
            ?.getAttributeNS(XMLConstants.XML_NS_URI, "id")?.takeIf(String::isNotBlank) ?: "v1"
    }

    fun part(line: EditableLyricLine, mainAgent: String): LyricVocalPart = line.vocalPart
        ?: if (line.agent.isBlank() || line.agent == mainAgent) LyricVocalPart.MAIN else LyricVocalPart.DUET

    fun normalize(root: Element, base: EditableLyrics, edits: Map<Int, EditableLyricLine>, targets: Map<Int, Element>) {
        val main = ensureAgent(root, "v1").apply { setAttribute("type", "person") }
        val duet = ensureAgent(root, "v2").apply { setAttribute("type", "other") }
        // Keep the main agent first for readers which choose the first declared person.
        main.parentNode.insertBefore(duet, main.parentNode.firstChild)
        main.parentNode.insertBefore(main, main.parentNode.firstChild)
        // Preserve unselected lines' sides while reducing their IDs to the same two sides.
        base.lines.forEach { original ->
            val line = edits[original.id] ?: original
            val id = if (part(line, base.mainAgent) == LyricVocalPart.MAIN) "v1" else "v2"
            targets.getValue(original.id).setAttributeNS(TTM, "ttm:agent", id)
        }
    }

    fun ensureAgent(root: Element, id: String): Element {
        val agents = root.getElementsByTagNameNS(TTM, "agent")
        (0 until agents.length).map { agents.item(it) as Element }
            .firstOrNull { it.getAttributeNS(XMLConstants.XML_NS_URI, "id") == id }?.let { return it }
        fun Element.container(name: String): Element = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .firstOrNull { it.localName == name }
            ?: ownerDocument.createElementNS(TT, name).also { insertBefore(it, firstChild) }
        return root.ownerDocument.createElementNS(TTM, "ttm:agent").apply {
            setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", id)
            setAttribute("type", "person")
            root.container("head").container("metadata").appendChild(this)
        }
    }
}
