package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsDocument
import org.w3c.dom.Element
import javax.xml.XMLConstants

/** Read-only preview data; the original document and XML are never rewritten. */
internal object LyricsMetadataReader {
    fun read(document: LyricsDocument): Map<String, List<String>> {
        val values = linkedMapOf<String, MutableList<String>>()
        fun add(key: String, value: String) {
            if (key.isNotBlank() && value.isNotBlank()) values.getOrPut(key) { mutableListOf() }.add(value.trim())
        }
        document.metadata.forEach { (key, entries) -> entries.forEach { add(key, it) } }
        val xml = document.rawTtml?.let { runCatching { TtmlXmlDocument.parse(it) }.getOrNull() }
        val head = xml?.getElementsByTagNameNS("*", "head")?.item(0) as? Element
        if (head != null) {
            val elements = head.getElementsByTagNameNS("*", "*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                when (element.localName) {
                    "meta" -> add(element.getAttribute("key").ifBlank { element.getAttribute("name") },
                        element.getAttribute("value").ifBlank { element.textContent })
                    "title", "desc", "copyright", "author" -> add(element.localName, element.textContent)
                    "agent" -> {
                        val id = element.getAttributeNS(XMLConstants.XML_NS_URI, "id")
                        val name = element.getElementsByTagNameNS("*", "name").item(0)?.textContent.orEmpty()
                        if (id.isNotBlank()) add("agent.$id", name)
                    }
                }
            }
        }
        return values.mapValues { (_, entries) -> entries.distinct() }
    }
}
