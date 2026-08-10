package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.math.abs

interface LyricCodec {
    fun parse(raw: String, source: LyricsSource): LyricsDocument
}

object LyricsTrackMerger {
    fun merge(
        original: LyricsDocument,
        translations: List<LyricLine> = emptyList(),
        romanizations: List<LyricLine> = emptyList(),
        toleranceMs: Long = 120,
    ): LyricsDocument = original.copy(
        lines = original.lines.mapIndexed { index, line ->
            line.copy(
                translation = findSecondary(line, index, translations, toleranceMs)?.text ?: line.translation,
                romanization = findSecondary(line, index, romanizations, toleranceMs)?.text ?: line.romanization,
            )
        },
    )

    private fun findSecondary(
        original: LyricLine,
        index: Int,
        candidates: List<LyricLine>,
        toleranceMs: Long,
    ): LyricLine? {
        val start = original.startMs
        if (start != null) {
            candidates.minByOrNull { abs((it.startMs ?: Long.MAX_VALUE / 2) - start) }
                ?.takeIf { it.startMs != null && abs(it.startMs - start) <= toleranceMs }
                ?.let { return it }
        }
        return candidates.getOrNull(index)
    }
}
