package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import kotlin.math.abs

/** Matches reliable background branches locally; these matches never influence the main backbone. */
internal object AuxiliaryLyricsAligner {
    fun align(primary: LyricsDocument, secondary: LyricsDocument): List<CrossSourceAlignmentGroup> {
        val primaryAux = primary.lines.withIndex().filter { it.value.background && it.value.text.isNotBlank() }
        val secondaryAux = secondary.lines.withIndex().filter { it.value.background && it.value.text.isNotBlank() }
        if (primaryAux.isEmpty() || secondaryAux.isEmpty()) return emptyList()

        val usedSecondary = mutableSetOf<Int>()
        return primaryAux.mapNotNull { primaryLine ->
            secondaryAux.asSequence()
                .filter { it.index !in usedSecondary }
                .map { secondaryLine -> candidate(primaryLine, secondaryLine) }
                .filter { it.score >= MIN_AUX_SCORE }
                .maxByOrNull(Candidate::score)
                ?.also { usedSecondary += it.secondary.index }
                ?.toGroup()
        }.sortedBy { it.primaryIndices.single() }
    }

    private fun candidate(
        primary: IndexedValue<LyricLine>,
        secondary: IndexedValue<LyricLine>,
    ): Candidate {
        val similarity = PerformanceAwareTextMatcher.similarity(primary.value.text, secondary.value.text)
        val primaryStart = primary.value.startMs
        val secondaryStart = secondary.value.startMs
        val timing = when {
            primaryStart == null || secondaryStart == null -> 0.0
            else -> (1.0 - abs(primaryStart - secondaryStart) / MAX_AUX_TIME_DISTANCE_MS.toDouble())
                .coerceIn(0.0, 1.0)
        }
        return Candidate(primary, secondary, similarity, timing, similarity * 0.9 + timing * 0.1)
    }

    private fun Candidate.toGroup() = CrossSourceAlignmentGroup(
        primaryIndices = listOf(primary.index),
        secondaryIndices = listOf(secondary.index),
        primaryLines = listOf(primary.value),
        secondaryLines = listOf(secondary.value),
        textSimilarity = similarity,
        timingScore = timing,
        confidence = score,
        matchedCharacterCount = minOf(
            PerformanceAwareTextMatcher.signature(primary.value.text).length,
            PerformanceAwareTextMatcher.signature(secondary.value.text).length,
        ),
    )

    private data class Candidate(
        val primary: IndexedValue<LyricLine>,
        val secondary: IndexedValue<LyricLine>,
        val similarity: Double,
        val timing: Double,
        val score: Double,
    )

    private const val MAX_AUX_TIME_DISTANCE_MS = 5_000L
    private const val MIN_AUX_SCORE = 0.82
}
