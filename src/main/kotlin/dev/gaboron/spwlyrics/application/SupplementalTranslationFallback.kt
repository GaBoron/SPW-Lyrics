package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument

/** Removes only translations added by cross-source enrichment, preserving source-native text. */
internal object SupplementalTranslationFallback {
    fun isAvailable(document: LyricsDocument): Boolean = supplementedIndices(document).isNotEmpty()

    fun apply(document: LyricsDocument): LyricsDocument {
        val indices = supplementedIndices(document)
        if (indices.isEmpty()) return document
        val metadata = document.metadata.toMutableMap().apply {
            remove(SecondaryLyricsEnricher.TRANSLATION_SOURCE_KEY)
            remove(SecondaryLyricsEnricher.SUPPLEMENTED_TRANSLATION_LINES_KEY)
        }
        return document.copy(
            lines = document.lines.mapIndexed { index, line ->
                if (index in indices) line.copy(translation = null) else line
            },
            metadata = metadata,
        )
    }

    private fun supplementedIndices(document: LyricsDocument): Set<Int> =
        document.metadata[SecondaryLyricsEnricher.SUPPLEMENTED_TRANSLATION_LINES_KEY]
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filterTo(mutableSetOf()) { it in document.lines.indices }
}
