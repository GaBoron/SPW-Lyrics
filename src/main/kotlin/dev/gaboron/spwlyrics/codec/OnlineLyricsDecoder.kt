package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.*

/** Preserve original TTML; choose a decoder from the payload rather than a provider's timing hint. */
internal object OnlineLyricsDecoder {
    fun parse(text: String, source: LyricsSource): LyricsDocument {
        val raw = text.trim()
        return when {
            raw.contains("<tt") || raw.contains(":tt ") -> TtmlCodec().parse(text, source)
            Regex("(?m)^\\[\\d+,\\d+]").containsMatchIn(raw) -> SodaLyricsCodec.parse(text, source)
            else -> LrcCodec.parse(text, source)
        }
    }
}
