package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.LyricsTrackMerger
import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument

internal object SecondaryLyricsEnricher {
    fun enrich(primary: LyricsDocument, secondary: LyricsDocument): LyricsDocument {
        val secondaryLines = secondary.lines.filterNot(LyricLine::background)
        val translations = secondaryLines.mapNotNull { line -> line.secondaryLine(LyricLine::translation) }
        val romanizations = secondaryLines.mapNotNull { line -> line.secondaryLine(LyricLine::romanization) }
        if (translations.isEmpty() && romanizations.isEmpty()) return primary

        val merged = LyricsTrackMerger.merge(primary, translations, romanizations)
        val metadata = merged.metadata.toMutableMap()
        if (translations.isNotEmpty()) {
            metadata[TRANSLATION_SOURCE_KEY] =
                (metadata[TRANSLATION_SOURCE_KEY].orEmpty() + secondary.source.displayName).distinct()
        }
        if (romanizations.isNotEmpty()) {
            metadata[ROMANIZATION_SOURCE_KEY] =
                (metadata[ROMANIZATION_SOURCE_KEY].orEmpty() + secondary.source.displayName).distinct()
        }
        return merged.copy(
            lines = merged.lines.mapIndexed { index, line ->
                val existing = primary.lines[index]
                line.copy(
                    translation = existing.translation ?: line.translation,
                    romanization = existing.romanization ?: line.romanization,
                )
            },
            metadata = metadata,
        )
    }

    fun needsTranslation(document: LyricsDocument): Boolean {
        val primary = document.lines.filter { !it.background && it.text.isNotBlank() }
        if (primary.isEmpty()) return false
        val translated = primary.count { !it.translation.isNullOrBlank() }
        return translated * 10 < primary.size * MIN_TRANSLATION_COVERAGE_TENTHS
    }

    private fun LyricLine.secondaryLine(selector: (LyricLine) -> String?): LyricLine? =
        selector(this)?.takeIf(String::isNotBlank)?.let { secondary ->
            LyricLine(startMs = startMs, endMs = endMs, text = secondary)
        }

    const val TRANSLATION_SOURCE_KEY = "translationSource"
    const val ROMANIZATION_SOURCE_KEY = "romanizationSource"
    private const val MIN_TRANSLATION_COVERAGE_TENTHS = 8
}
