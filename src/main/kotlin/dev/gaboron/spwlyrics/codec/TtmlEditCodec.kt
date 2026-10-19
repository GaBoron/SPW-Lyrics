package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.*
import org.w3c.dom.Element
import org.w3c.dom.Document
import org.w3c.dom.Node
import javax.xml.XMLConstants

/** Patches selected DOM nodes, leaving unrelated TTML extensions and lyric rows intact. */
internal object TtmlEditCodec {
    private const val TT = "http://www.w3.org/ns/ttml"
    private const val TTM = "http://www.w3.org/ns/ttml#metadata"
    private val translations = setOf("translation", "x-translation")
    private val romanizations = setOf("romanization", "transliteration", "x-roman")
    private val backgrounds = setOf("background", "x-bg")

    fun open(document: LyricsDocument): EditableLyrics {
        val raw = document.rawTtml ?: MetadataTtmlWriter.write(document)
        return openRaw(raw)
    }

    fun openRaw(raw: String): EditableLyrics {
        val xml = TtmlXmlDocument.parse(raw)
        val rows = mutableListOf<EditableLyricLine>()
        fun row(element: Element) {
            val wordNodes = mutableListOf<Element>()
            fun words(parent: Element) { parent.elements().forEach { child ->
                if (child.role() in translations + romanizations + backgrounds) return@forEach
                if (child.localName == "span" && child.time("begin") != null && child.endTime() != null) wordNodes += child
                else words(child)
            } }
            words(element)
            val text = element.lyricText()
            val start = element.time("begin") ?: wordNodes.firstOrNull()?.time("begin")
            val end = element.endTime() ?: wordNodes.lastOrNull()?.endTime()
            rows += EditableLyricLine(rows.size, element.path(), LyricEditTime.format(start), LyricEditTime.format(end),
                text, generateSequence(element as Node?) { it.parentNode }.filterIsInstance<Element>()
                    .map { it.attribute("agent") }.firstOrNull(String::isNotBlank).orEmpty(),
                element.role() in backgrounds,
                element.elements().firstOrNull { it.role() in translations }?.textContent.orEmpty(),
                element.elements().firstOrNull { it.role() in romanizations }?.textContent.orEmpty(),
                wordNodes.map { word -> EditableLyricWord(word.path(), LyricEditTime.format(word.time("begin")),
                    LyricEditTime.format(word.endTime()), word.lyricText(trim = false), word.elements().isNotEmpty()) })
        }
        val paragraphs = xml.getElementsByTagNameNS("*", "p")
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as Element
            if (paragraph.role() in translations + romanizations) continue
            row(paragraph)
            fun nested(parent: Element) { parent.elements().forEach { child ->
                if (child.role() in backgrounds) row(child)
                nested(child)
            } }
            nested(paragraph)
        }
        return EditableLyrics(raw, rows, TtmlVocalParts.mainAgent(xml.documentElement))
    }

    fun apply(base: EditableLyrics, edits: Map<Int, EditableLyricLine>,
        transform: (Document, Map<Int, Element>) -> Unit = { _, _ -> }): String {
        val xml = TtmlXmlDocument.parse(base.raw)
        xml.documentElement.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ttm", TTM)
        val targets = base.lines.associate { it.id to resolve(xml.documentElement, it.path) }.toMutableMap()
        val wordTargets = base.lines.flatMap { it.words }.associate { it.path to resolve(xml.documentElement, it.path) }
        val sidesChanged = edits.values.any { it.vocalPart != null }
        if (sidesChanged) TtmlVocalParts.normalize(xml.documentElement, base, edits, targets)
        edits.forEach { (id, edited) ->
            val original = base.lines.first { it.id == id }
            var element = targets.getValue(id)
            val start = LyricEditTime.parse(edited.start)
            val end = LyricEditTime.parse(edited.end)
            val lineTimeChanged = edited.start != original.start || edited.end != original.end
            if (lineTimeChanged) require(start == null || end == null || end >= start) { "第 ${id + 1} 句结束时间不能早于开始时间。" }
            if (edited.start != original.start) element.setTime("begin", start)
            if (edited.end != original.end) { element.setTime("end", end); element.removeAttribute("dur") }
            if (!sidesChanged && edited.agent != original.agent) {
                require(edited.agent.isEmpty() || edited.agent.matches(Regex("[A-Za-z_][A-Za-z0-9_.-]*"))) { "声部标识请使用 v1、v2 等字母数字标识。" }
                if (edited.agent.isBlank()) element.removeLocalAttribute("agent")
                else {
                    xml.documentElement.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ttm", TTM)
                    element.setAttributeNS(TTM, "ttm:agent", edited.agent)
                    TtmlVocalParts.ensureAgent(xml.documentElement, edited.agent)
                }
            }
            if (edited.background != original.background) {
                if (edited.background) element.setAttributeNS(TTM, "ttm:role", "x-bg")
                else {
                    element.removeLocalAttribute("role")
                    if (element.localName != "p") {
                        val paragraph = generateSequence(element.parentNode) { it.parentNode }.filterIsInstance<Element>().first { it.localName == "p" }
                        val promoted = xml.createElementNS(TT, "p")
                        (0 until element.attributes.length).map { element.attributes.item(it) }.forEach {
                            promoted.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue)
                        }
                        val agent = element.getAttributeNS(TTM, "agent").ifBlank { edited.agent }
                        if (agent.isNotBlank()) promoted.setAttributeNS(TTM, "ttm:agent", agent)
                        while (element.hasChildNodes()) promoted.appendChild(element.firstChild)
                        paragraph.parentNode.insertBefore(promoted, paragraph.nextSibling)
                        element.parentNode.removeChild(element)
                        element = promoted
                        targets[id] = promoted
                    }
                }
            }
            if (edited.text != original.text || original.words.isNotEmpty() && edited.words.isEmpty()) {
                require(edited.words.isEmpty()) { "替换整句正文时需清除原逐字时间。" }
                val preserved = element.elements().filter { it.role() in translations + romanizations + backgrounds }
                targets.values.filter { row -> row !== element && row.role() in backgrounds &&
                    generateSequence(row.parentNode) { it.parentNode }.any { it === element } }
                    .filter { it !in preserved }.forEach { element.appendChild(it) }
                val keep = preserved + element.elements().filter { it.role() in backgrounds }
                (0 until element.childNodes.length).map { element.childNodes.item(it) }.filter { it !in keep }.forEach(element::removeChild)
                element.insertBefore(xml.createTextNode(edited.text), element.firstChild)
            }
            edited.words.forEachIndexed { index, word ->
                val old = original.words[index]
                val wordStart = LyricEditTime.parse(word.start) ?: error("逐字开始时间不能为空。")
                val wordEnd = LyricEditTime.parse(word.end) ?: error("逐字结束时间不能为空。")
                if (lineTimeChanged || word.start != old.start || word.end != old.end) {
                    require(wordEnd >= wordStart) { "第 ${index + 1} 个词的结束时间不能早于开始时间。" }
                    require((start == null || wordStart >= start) && (end == null || wordEnd <= end)) { "逐字时间超出本句范围；可使用整句平移按钮一起调整。" }
                }
                val target = wordTargets.getValue(word.path)
                if (word.start != old.start) target.setTime("begin", wordStart)
                if (word.end != old.end) { target.setTime("end", wordEnd); target.removeAttribute("dur") }
                if (word.text != old.text) {
                    require(!old.hasMarkup) { "含注音等扩展信息的单词，请在原文中修改文本。" }
                    target.textContent = word.text
                }
            }
            element.secondary(translations, "x-translation", original.translation, edited.translation)
            element.secondary(romanizations, "x-roman", original.romanization, edited.romanization)
        }
        transform(xml, targets)
        return TtmlXmlDocument.write(xml)
    }

    private fun Element.secondary(roles: Set<String>, role: String, before: String, after: String) {
        if (before == after) return
        val existing = elements().firstOrNull { it.role() in roles }
        if (after.isEmpty()) { existing?.let(::removeChild); return }
        val target = existing ?: ownerDocument.createElementNS(TT, "span").also { it.setAttributeNS(TTM, "ttm:role", role); appendChild(it) }
        target.textContent = after
    }
    private fun Element.setTime(name: String, time: Long?) {
        if (time == null) removeLocalAttribute(name) else setAttribute(name, LyricEditTime.format(time))
    }
    private fun Element.removeLocalAttribute(name: String) {
        (0 until attributes.length).map { attributes.item(it) }.filter { it.localName == name || it.nodeName == name }.forEach { removeAttributeNode(it as org.w3c.dom.Attr) }
    }
    private fun Element.time(name: String) = attribute(name).takeIf(String::isNotBlank)?.let {
        LyricEditTime.parse(if (it.matches(Regex("[0-9.]+"))) "${it}s" else it)
    }
    private fun Element.endTime(): Long? = time("end") ?: time("dur")?.let { duration -> time("begin")?.plus(duration) }
    private fun Element.attribute(name: String) = (0 until attributes.length).map { attributes.item(it) }.firstOrNull { it.localName == name || it.nodeName == name }?.nodeValue.orEmpty()
    private fun Element.role() = attribute("role")
    private fun Element.elements() = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.lyricText(trim: Boolean = true): String = buildString {
        fun walk(node: Node) {
            if (node is Element && node !== this@lyricText && node.role() in translations + romanizations + backgrounds + setOf("x-rt", "x-rt-before", "x-rt-after")) return
            if (node.nodeType == Node.TEXT_NODE) append(node.nodeValue)
            else if (node is Element && node.localName == "br") append('\n')
            else for (i in 0 until node.childNodes.length) walk(node.childNodes.item(i))
        }
        walk(this@lyricText)
    }.let { if (trim) it.trim() else it }
    private fun Element.path(): List<Int> = (parentNode as? Element)?.let { it.path() + it.elements().indexOf(this) } ?: emptyList()
    private fun resolve(root: Element, path: List<Int>): Element = path.fold(root) { parent, index -> parent.elements()[index] }
}
