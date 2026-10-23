package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.LrcCodec
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource

/** Convert TTML and explicit instrumental notices; ordinary local LRC stays with SPW. */
internal class LocalLyricsConversion(private val ttml: LocalTtmlConversion = LocalTtmlConversion()) {
    fun parse(raw: String): LyricsDocument? = ttml.parse(raw) ?: runCatching {
        LrcCodec.parse(raw, LyricsSource.LOCAL).takeIf { it.isInstrumental }
    }.getOrNull()
}
