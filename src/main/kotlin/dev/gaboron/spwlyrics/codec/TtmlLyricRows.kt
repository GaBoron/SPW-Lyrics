package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import org.w3c.dom.Element

/** Structural edits share the same DOM transaction as field edits, so paths never become stale. */
internal object TtmlLyricRows {
    private const val TT = "http://www.w3.org/ns/ttml"
    private const val EMPTY = "<tt xmlns=\"$TT\"><body><div/></body></tt>"

    fun add(base: EditableLyrics?, edits: Map<Int, EditableLyricLine>, after: Int?): Pair<EditableLyrics, Int> {
        var addedPath: List<Int> = emptyList()
        val raw = TtmlEditCodec.apply(base ?: TtmlEditCodec.openRaw(EMPTY), edits) { xml, targets ->
            val row = xml.createElementNS(TT, "p")
            val selected = targets[after]
            val paragraph = selected?.let { generateSequence(it as org.w3c.dom.Node?) { node -> node.parentNode }
                .filterIsInstance<Element>().firstOrNull { it.localName == "p" } }
            if (paragraph != null) paragraph.parentNode.insertBefore(row, paragraph.nextSibling)
            else {
                val div = xml.getElementsByTagNameNS("*", "div").item(0)
                    ?: (xml.getElementsByTagNameNS("*", "body").item(0)
                        ?: xml.documentElement.appendChild(xml.createElementNS(TT, "body"))).appendChild(xml.createElementNS(TT, "div"))
                div.appendChild(row)
            }
            // Store a structural path after insertion; formatting only adds text nodes.
            addedPath = path(row)
        }
        val opened = TtmlEditCodec.openRaw(raw)
        return opened to opened.lines.first { it.path == addedPath }.id
    }

    fun delete(base: EditableLyrics, edits: Map<Int, EditableLyricLine>, ids: Set<Int>): EditableLyrics {
        val raw = TtmlEditCodec.apply(base, edits - ids) { _, targets ->
            val removed = ids.mapNotNull(targets::get).toSet()
            // A background row has its own selection: preserve it when only its parent is deleted.
            targets.values.filter { it !in removed && generateSequence(it.parentNode) { node -> node.parentNode }.any { node -> node in removed } }
                .filter { row -> generateSequence(row.parentNode) { it.parentNode }.filterIsInstance<Element>().none { it in targets.values && it !in removed } }
                .forEach { row ->
                    val ancestor = generateSequence(row.parentNode) { it.parentNode }.filterIsInstance<Element>().last { it in removed }
                    val promoted = row.ownerDocument.createElementNS(TT, "p")
                    (0 until row.attributes.length).map { row.attributes.item(it) }.forEach { promoted.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue) }
                    val agent = row.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "agent").ifBlank {
                        base.lines.firstOrNull { targets[it.id] === row }?.let { edits[it.id] ?: it }?.agent.orEmpty()
                    }
                    if (agent.isNotBlank()) promoted.setAttributeNS("http://www.w3.org/ns/ttml#metadata", "ttm:agent", agent)
                    while (row.hasChildNodes()) promoted.appendChild(row.firstChild)
                    ancestor.parentNode.insertBefore(promoted, ancestor)
                }
            removed.forEach { it.parentNode?.removeChild(it) }
        }
        val result = TtmlEditCodec.openRaw(raw)
        return if (result.lines.isEmpty()) result.copy(raw = "") else result
    }

    private fun path(row: Element): List<Int> = (row.parentNode as? Element)?.let { parent ->
        path(parent) + (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }.indexOf(row)
    } ?: emptyList()
}
