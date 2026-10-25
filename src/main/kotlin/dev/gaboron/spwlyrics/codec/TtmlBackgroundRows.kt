package dev.gaboron.spwlyrics.codec

import org.w3c.dom.Element
import org.w3c.dom.Node

/** Converts the editor's flat rows into TTML Tool's main paragraph / background span pairs. */
internal object TtmlBackgroundRows {
    private const val TT = "http://www.w3.org/ns/ttml"
    private const val TTM = "http://www.w3.org/ns/ttml#metadata"
    private val backgrounds = setOf("background", "x-bg")

    data class Row(val element: Element, val background: Boolean)

    fun arrange(rows: List<Row>): List<Element> {
        var previousParagraph: Element? = null
        var previousWasBackground = false
        var mainAgent = ""
        return rows.map { row ->
            val attach = row.background && previousParagraph != null && !previousWasBackground
            val original = row.element
            val originalAgent = original.effectiveAgent()
            val wasNested = generateSequence(original.parentNode) { it.parentNode }
                .filterIsInstance<Element>().any { it.localName == "p" }
            val element = original.ownerDocument.renameNode(original, TT, if (attach) "span" else "p") as Element
            if (attach) {
                element.removeLocalAttribute("role")
                element.setAttributeNS(TTM, "ttm:role", "x-bg")
                // A background vocal shares the main row's side, as in the upstream export.
                element.removeLocalAttribute("agent")
                previousParagraph.appendChild(element)
            } else {
                if (element.attribute("role") in backgrounds) element.removeLocalAttribute("role")
                if (wasNested) {
                    val previous = checkNotNull(previousParagraph)
                    previous.parentNode.insertBefore(element, previous.nextSibling)
                }
                val inherited = if (row.background && previousParagraph != null) mainAgent else originalAgent
                if (wasNested || row.background && previousParagraph != null) {
                    element.removeLocalAttribute("agent")
                    if (inherited.isNotBlank()) element.setAttributeNS(TTM, "ttm:agent", inherited)
                }
                if (!row.background || previousParagraph == null) mainAgent = inherited
                previousParagraph = element
            }
            // First-row and consecutive background flags are promoted without dropping their content.
            previousWasBackground = row.background
            element
        }
    }

    private fun Element.effectiveAgent(): String = generateSequence(this as Node?) { it.parentNode }
        .filterIsInstance<Element>().map { it.attribute("agent") }.firstOrNull(String::isNotBlank).orEmpty()

    private fun Element.attribute(name: String): String = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == name || it.nodeName == name }?.nodeValue.orEmpty()

    private fun Element.removeLocalAttribute(name: String) {
        (0 until attributes.length).map { attributes.item(it) }
            .filter { it.localName == name || it.nodeName == name }
            .forEach { removeAttributeNode(it as org.w3c.dom.Attr) }
    }
}
