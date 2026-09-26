package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsSource

/** Converts only recognizable, usable TTML; null leaves SPW's local loader in charge. */
internal class LocalTtmlConversion(private val codec: TtmlCodec = TtmlCodec()) {
    fun convert(raw: String): String? {
        if (raw.length > MAX_TTML_CHARS || !looksLikeTtml(raw)) return null
        return runCatching {
            val document = codec.parse(raw, LyricsSource.LOCAL)
            if (document.lines.isEmpty()) null else SpwLyricsEncoder.encode(document).takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun looksLikeTtml(raw: String): Boolean {
        val opening = raw.take(4096).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .replaceFirst(XML_DECLARATION, "")
            .replace(LEADING_COMMENTS, "")
            .trimStart()
        return TT_ROOT.containsMatchIn(opening)
    }

    private companion object {
        const val MAX_TTML_CHARS = 2_000_000
        val XML_DECLARATION = Regex("^<\\?xml\\b[^?]*\\?>", RegexOption.IGNORE_CASE)
        val LEADING_COMMENTS = Regex("^(?:\\s*<!--[\\s\\S]*?-->)*")
        val TT_ROOT = Regex("^<(?:[A-Za-z_][\\w.-]*:)?tt(?=[\\s>/])", RegexOption.IGNORE_CASE)
    }
}
