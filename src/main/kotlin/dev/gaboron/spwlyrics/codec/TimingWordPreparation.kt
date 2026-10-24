package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.TimingSplit
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.text.BreakIterator
import java.util.Locale

internal data class TimingPreparation(val raw: String, val prepared: Int, val skipped: Int)

/** Add timing spans only to plain lyric rows; existing timed words and XML extensions stay intact. */
internal object TimingWordPreparation {
    fun prepare(base: EditableLyrics, mode: TimingSplit): TimingPreparation {
        var prepared = 0
        var skipped = 0
        val raw = TtmlEditCodec.apply(base, emptyMap()) { xml, targets ->
            base.lines.filter { it.words.isEmpty() && it.text.isNotBlank() }.forEach { line ->
                val element = targets.getValue(line.id)
                val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
                val annotations = children.filterIsInstance<Element>().filter { child ->
                    (0 until child.attributes.length).any { child.attributes.item(it).let { a ->
                        (a.localName == "role" || a.nodeName == "role") && a.nodeValue in setOf("x-translation", "x-roman", "x-bg", "background", "translation", "romanization")
                    } }
                }
                val preserved = children.filter { it in annotations || it.nodeType == Node.COMMENT_NODE || it.nodeType == Node.PROCESSING_INSTRUCTION_NODE }
                if (children.filterIsInstance<Element>().any { it !in preserved && it.localName != "br" }) { skipped++; return@forEach }
                val start = LyricEditTime.parse(line.start) ?: 0L
                val end = maxOf(LyricEditTime.parse(line.end) ?: start, start)
                val plainText = buildString {
                    children.filter { it !in preserved }.forEach { child ->
                        if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) append(child.nodeValue)
                        else if (child is Element && child.localName == "br") append('\n')
                    }
                }
                children.filter { it !in preserved }.forEach(element::removeChild)
                val anchor = preserved.firstOrNull()
                tokens(plainText, mode).forEach { text ->
                    val node = if (text.isBlank()) xml.createTextNode(text) else xml.createElementNS(element.namespaceURI, "span").apply {
                        setAttribute("begin", LyricEditTime.format(start)); setAttribute("end", LyricEditTime.format(end)); textContent = text
                    }
                    element.insertBefore(node, anchor)
                }
                prepared++
            }
        }
        return TimingPreparation(raw, prepared, skipped)
    }

    fun tokens(text: String, mode: TimingSplit): List<String> {
        if (mode == TimingSplit.WHITESPACE) return Regex("(?U)\\s+|\\S+").findAll(text).map { it.value }.toList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val value = text.substring(start, end)
            if (value.matches(Regex("[\\p{P}]+")) && result.lastOrNull()?.isNotBlank() == true) result[result.lastIndex] += value
            else result += value
            start = end; end = iterator.next()
        }
        return result
    }
}
