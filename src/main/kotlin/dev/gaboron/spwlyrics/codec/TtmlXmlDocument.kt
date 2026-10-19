package dev.gaboron.spwlyrics.codec

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Formats structural containers only: indentation inside a lyric paragraph would change its text. */
internal object TtmlXmlDocument {
    fun parse(raw: String): Document {
        require(raw.length <= 4_000_000) { "歌词过大。" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(raw)))
    }

    fun write(xml: Document): String {
        fun indent(element: Element, depth: Int) {
            if (element.localName !in setOf("tt", "head", "metadata", "body", "div")) return
            val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
            if (children.any { it.nodeType == Node.TEXT_NODE && it.nodeValue.isNotBlank() }) return
            children.filter { it.nodeType == Node.TEXT_NODE && it.nodeValue.isBlank() }.forEach(element::removeChild)
            val remaining = (0 until element.childNodes.length).map { element.childNodes.item(it) }
            remaining.forEach { child ->
                element.insertBefore(xml.createTextNode("\n" + "  ".repeat(depth + 1)), child)
                if (child is Element) indent(child, depth + 1)
            }
            if (remaining.isNotEmpty()) element.appendChild(xml.createTextNode("\n" + "  ".repeat(depth)))
        }
        indent(xml.documentElement, 0)
        val transformer = TransformerFactory.newInstance().apply {
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        return StringWriter().also { transformer.transform(DOMSource(xml), StreamResult(it)) }.toString()
            .replaceFirst(Regex("^(<\\?xml[^?]*\\?>)(?=<)"), "$1\n")
    }
}
