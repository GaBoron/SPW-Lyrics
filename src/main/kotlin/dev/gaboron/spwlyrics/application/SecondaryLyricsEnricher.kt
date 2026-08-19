package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument

internal object SecondaryLyricsEnricher {
    fun enrich(
        primary: LyricsDocument,
        secondary: LyricsDocument,
        alignment: CrossSourceAlignment = CrossSourceLyricsAligner.align(primary, secondary),
    ): LyricsDocument {
        val projection = TranslationProjectionPolicy.project(alignment)
        if (projection.translations.isEmpty() && projection.romanizations.isEmpty()) return primary
        var addedTranslations = false
        var addedRomanizations = false
        val lines = primary.lines.mapIndexed { index, line ->
            val translation = line.translation?.takeIf(String::isNotBlank)
                ?: projection.translations[index]
            val romanization = line.romanization?.takeIf(String::isNotBlank)
                ?: projection.romanizations[index]
            if (line.translation.isNullOrBlank() && translation != null) addedTranslations = true
            if (line.romanization.isNullOrBlank() && romanization != null) addedRomanizations = true
            line.copy(translation = translation, romanization = romanization)
        }
        if (!addedTranslations && !addedRomanizations) return primary

        val metadata = primary.metadata.toMutableMap()
        if (addedTranslations) {
            metadata[TRANSLATION_SOURCE_KEY] =
                (metadata[TRANSLATION_SOURCE_KEY].orEmpty() + secondary.source.displayName).distinct()
        }
        if (addedRomanizations) {
            metadata[ROMANIZATION_SOURCE_KEY] =
                (metadata[ROMANIZATION_SOURCE_KEY].orEmpty() + secondary.source.displayName).distinct()
        }
        return primary.copy(lines = lines, metadata = metadata)
    }

    fun needsTranslation(document: LyricsDocument): Boolean {
        val primary = document.lines.filter { !it.background && it.text.isNotBlank() }
        if (primary.isEmpty()) return false
        val translated = primary.count { !it.translation.isNullOrBlank() }
        return translated * 10 < primary.size * MIN_TRANSLATION_COVERAGE_TENTHS
    }

    const val TRANSLATION_SOURCE_KEY = "translationSource"
    const val ROMANIZATION_SOURCE_KEY = "romanizationSource"
    private const val MIN_TRANSLATION_COVERAGE_TENTHS = 8
}
