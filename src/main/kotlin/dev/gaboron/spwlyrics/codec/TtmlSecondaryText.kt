package dev.gaboron.spwlyrics.codec

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Resolves both inline secondary text and Apple metadata tracks linked by itunes:key. */
internal class TtmlSecondaryText(private val xml: Document) {
    private val linked = buildMap<Pair<String, String>, MutableList<Element>> {
        val texts = xml.getElementsByTagNameNS(ITUNES, "text")
        for (index in 0 until texts.length) {
            val text = texts.item(index) as Element
            val parent = text.parentNode as? Element ?: continue
            val kind = when (parent.localName) {
                "translation" -> "x-translation"
                "transliteration" -> "x-roman"
                else -> continue
            }
            val key = text.getAttribute("for")
            if (key.isNotBlank()) getOrPut(key to kind) { mutableListOf() }.add(text)
        }
    }

    fun read(element: Element, roles: Set<String>): String = targets(element, roles)
        .map { text(it).trim() }.filter(String::isNotBlank).distinct().joinToString("\n")

    fun write(element: Element, roles: Set<String>, role: String, before: String, after: String) {
        if (before == after) return
        val existing = targets(element, roles)
        if (after.isEmpty()) {
            existing.forEach { it.parentNode.removeChild(it) }
            return
        }
        val target = existing.firstOrNull() ?: xml.createElementNS(TT, "span").also {
            it.setAttributeNS(TTM, "ttm:role", role)
            element.appendChild(it)
        }
        target.textContent = after
        // The editor exposes one field per line; remove superseded copies for this line only.
        existing.drop(1).forEach { it.parentNode.removeChild(it) }
    }

    private fun targets(element: Element, roles: Set<String>): List<Element> {
        val inline = mutableListOf<Element>()
        fun visit(parent: Element) {
            for (index in 0 until parent.childNodes.length) {
                val child = parent.childNodes.item(index) as? Element ?: continue
                val role = child.getAttributeNS(TTM, "role").ifBlank { child.getAttribute("role") }.lowercase()
                when {
                    role in roles -> inline += child
                    role !in SECONDARY_ROLES && role !in BACKGROUND_ROLES -> visit(child)
                }
            }
        }
        visit(element)
        val key = element.getAttributeNS(ITUNES, "key")
        val kind = if ("x-translation" in roles) "x-translation" else "x-roman"
        return inline + linked[key to kind].orEmpty().filter { it.parentNode != null }
    }

    private fun text(element: Element): String = buildString {
        fun visit(node: Node) {
            when {
                node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE -> append(node.nodeValue)
                node is Element && node.localName == "br" -> append('\n')
                node is Element && node.getAttributeNS(TTM, "role").ifBlank { node.getAttribute("role") }.lowercase() in BACKGROUND_ROLES -> Unit
                else -> for (index in 0 until node.childNodes.length) visit(node.childNodes.item(index))
            }
        }
        visit(element)
    }

    companion object {
        private const val TT = "http://www.w3.org/ns/ttml"
        private const val TTM = "http://www.w3.org/ns/ttml#metadata"
        private const val ITUNES = "http://music.apple.com/lyric-ttml-internal"
        val TRANSLATIONS = setOf("translation", "x-translation")
        val ROMANIZATIONS = setOf("romanization", "transliteration", "x-roman")
        private val SECONDARY_ROLES = TRANSLATIONS + ROMANIZATIONS
        private val BACKGROUND_ROLES = setOf("background", "x-bg")
    }
}
