package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Converts parsed search results, never inventing word or line timing. */
internal object MetadataLyricsExport {
    /** Describes information that the selected text format cannot represent before any file write. */
    fun losses(document: LyricsDocument, format: MetadataLyricsFormat): List<String> {
        if (format == MetadataLyricsFormat.TTML) return emptyList()
        val root = document.rawTtml?.let { TtmlXmlDocument.parse(it).documentElement }
        val mainAgent = root?.let(TtmlVocalParts::mainAgent) ?: "v1"
        return buildList {
            if (document.lines.any { !it.agent.isNullOrBlank() && it.agent != mainAgent }) add("对唱声部将按普通歌词行保存；TTML 可保留声部标记。")
            if (document.lines.any { it.background }) add("背景人声将按普通歌词行保存；TTML 可保留背景标记。")
            if (format == MetadataLyricsFormat.LRC && document.lines.any { it.words.isNotEmpty() }) {
                add("逐字时间将退为逐行时间；增强 LRC、SPL、TTML 均可保留逐字时间。")
            }
            if (root != null) {
                val elements = root.getElementsByTagName("*")
                val hasRuby = (0 until elements.length).any { index ->
                    val attributes = elements.item(index).attributes
                    (0 until attributes.length).any { attributes.item(it).localName == "ruby" }
                }
                if (hasRuby) add("注音标记无法保留；TTML 可保留注音标记。")
            }
        }
    }

    fun encode(document: LyricsDocument, format: MetadataLyricsFormat): String {
        require(document.lines.isNotEmpty()) { "没有可以转换的歌词。" }
        if (format == MetadataLyricsFormat.TTML) return document.rawTtml?.let { TtmlXmlDocument.write(TtmlXmlDocument.parse(it)) }
            ?: MetadataTtmlWriter.write(document)
        require(document.lines.all { it.startMs != null }) { "歌词缺少时间轴，请选择 TTML；无法生成 LRC 或 SPL 时间。" }
        return document.lines.sortedWith(compareBy<LyricLine> { it.startMs }.thenBy { it.background }).flatMap { line ->
            val start = line.startMs!!
            val primary = buildString {
                append(SpwLyricsEncoder.timestamp(start))
                if (format == MetadataLyricsFormat.LRC || line.words.isEmpty()) append(line.text.replace('\n', ' '))
                else appendTimedText(line)
                line.effectiveEndMs()?.let { end ->
                    if (end > start) when (format) {
                        MetadataLyricsFormat.SPL -> append(SpwLyricsEncoder.timestamp(end))
                        MetadataLyricsFormat.ENHANCED_LRC -> append(SpwLyricsEncoder.inlineTimestamp(end))
                        else -> Unit
                    }
                }
            }
            listOf(primary) + listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).map {
                SpwLyricsEncoder.timestamp(start) + it.replace('\n', ' ')
            }
        }.joinToString("\n")
    }

    private fun StringBuilder.appendTimedText(line: LyricLine) {
        var cursor = 0
        line.words.forEach { word ->
            val index = line.text.indexOf(word.text, cursor)
            require(index >= cursor && word.text.isNotEmpty()) { "逐字文本与歌词正文不一致，无法无损转换；请选择 TTML。" }
            append(line.text.substring(cursor, index))
            append(SpwLyricsEncoder.inlineTimestamp(word.startMs))
            append(word.text.replace('\n', ' '))
            cursor = index + word.text.length
        }
        append(line.text.substring(cursor).replace('\n', ' '))
    }
}
