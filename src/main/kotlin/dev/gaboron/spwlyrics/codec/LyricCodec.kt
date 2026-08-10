package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource

interface LyricCodec {
    fun parse(raw: String, source: LyricsSource): LyricsDocument
}

object LyricsTrackMerger {
    fun merge(
        original: LyricsDocument,
        translations: List<LyricLine> = emptyList(),
        romanizations: List<LyricLine> = emptyList(),
        toleranceMs: Long = 1_200,
    ): LyricsDocument {
        val alignedTranslations = SecondaryLyricsAligner.align(original.lines, translations, toleranceMs)
        val alignedRomanizations = SecondaryLyricsAligner.align(original.lines, romanizations, toleranceMs)
        return original.copy(
            lines = original.lines.mapIndexed { index, line ->
                line.copy(
                    translation = alignedTranslations[index]?.text ?: line.translation,
                    romanization = alignedRomanizations[index]?.text ?: line.romanization,
                )
            },
        )
    }
}
