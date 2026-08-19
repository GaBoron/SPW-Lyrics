package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TextNormalizer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Finds a globally ordered alignment between differently segmented primary lyric tracks. */
internal object CrossSourceLyricsAligner {
    fun align(primary: LyricsDocument, secondary: LyricsDocument): CrossSourceAlignment {
        val primaryLines = primary.lines.withIndex()
            .filter { (_, line) -> !line.background && line.text.isNotBlank() }
        val secondaryLines = secondary.lines.withIndex()
            .filter { (_, line) -> !line.background && line.text.isNotBlank() }
        if (primaryLines.isEmpty() || secondaryLines.isEmpty()) {
            return CrossSourceAlignment(
                groups = emptyList(),
                primaryLineCount = primaryLines.size,
                secondaryLineCount = secondaryLines.size,
                primaryCharacterCount = primaryLines.sumOf { TextNormalizer.compact(it.value.text).length },
                secondaryCharacterCount = secondaryLines.sumOf { TextNormalizer.compact(it.value.text).length },
            )
        }

        val timeOffsetMs = estimateTimeOffset(primaryLines, secondaryLines)
        val totals = Array(primaryLines.size + 1) {
            DoubleArray(secondaryLines.size + 1) { NEGATIVE_INFINITY }
        }
        val previous = Array(primaryLines.size + 1) {
            arrayOfNulls<AlignmentStep>(secondaryLines.size + 1)
        }
        totals[0][0] = 0.0

        fun update(primaryIndex: Int, secondaryIndex: Int, score: Double, step: AlignmentStep) {
            if (score > totals[primaryIndex][secondaryIndex]) {
                totals[primaryIndex][secondaryIndex] = score
                previous[primaryIndex][secondaryIndex] = step
            }
        }

        for (primaryIndex in 0..primaryLines.size) {
            for (secondaryIndex in 0..secondaryLines.size) {
                val current = totals[primaryIndex][secondaryIndex]
                if (current == NEGATIVE_INFINITY) continue
                if (primaryIndex < primaryLines.size) {
                    update(
                        primaryIndex + 1,
                        secondaryIndex,
                        current + SKIP_PENALTY,
                        AlignmentStep(primaryCount = 1, secondaryCount = 0),
                    )
                }
                if (secondaryIndex < secondaryLines.size) {
                    update(
                        primaryIndex,
                        secondaryIndex + 1,
                        current + SKIP_PENALTY,
                        AlignmentStep(primaryCount = 0, secondaryCount = 1),
                    )
                }
                for (primaryCount in 1..minOf(MAX_GROUP_LINES, primaryLines.size - primaryIndex)) {
                    for (secondaryCount in 1..minOf(MAX_GROUP_LINES, secondaryLines.size - secondaryIndex)) {
                        val primaryGroup = primaryLines.subList(primaryIndex, primaryIndex + primaryCount)
                        val secondaryGroup = secondaryLines.subList(secondaryIndex, secondaryIndex + secondaryCount)
                        val group = scoreGroup(primaryGroup, secondaryGroup, timeOffsetMs) ?: continue
                        update(
                            primaryIndex + primaryCount,
                            secondaryIndex + secondaryCount,
                            current + group.pathScore,
                            AlignmentStep(primaryCount, secondaryCount, group.alignmentGroup),
                        )
                    }
                }
            }
        }

        val groups = mutableListOf<CrossSourceAlignmentGroup>()
        var primaryIndex = primaryLines.size
        var secondaryIndex = secondaryLines.size
        while (primaryIndex > 0 || secondaryIndex > 0) {
            val step = previous[primaryIndex][secondaryIndex] ?: break
            step.group?.let(groups::add)
            primaryIndex -= step.primaryCount
            secondaryIndex -= step.secondaryCount
        }
        return CrossSourceAlignment(
            groups = groups.reversed(),
            primaryLineCount = primaryLines.size,
            secondaryLineCount = secondaryLines.size,
            primaryCharacterCount = primaryLines.sumOf { TextNormalizer.compact(it.value.text).length },
            secondaryCharacterCount = secondaryLines.sumOf { TextNormalizer.compact(it.value.text).length },
        )
    }

    private fun scoreGroup(
        primary: List<IndexedValue<LyricLine>>,
        secondary: List<IndexedValue<LyricLine>>,
        timeOffsetMs: Long,
    ): ScoredGroup? {
        if (!groupBoundariesAreCompatible(primary.map(IndexedValue<LyricLine>::value))) return null
        if (!groupBoundariesAreCompatible(secondary.map(IndexedValue<LyricLine>::value))) return null

        val primaryText = compactGroup(primary)
        val secondaryText = compactGroup(secondary)
        if (primaryText.isEmpty() || secondaryText.isEmpty()) return null

        val similarity = TextNormalizer.similarity(
            primary.joinToString(" ") { it.value.text },
            secondary.joinToString(" ") { it.value.text },
        )
        val lengthBalance = minOf(primaryText.length, secondaryText.length).toDouble() /
            maxOf(primaryText.length, secondaryText.length)
        val timingScore = timingScore(primary, secondary, timeOffsetMs)
        val exact = primaryText == secondaryText
        val isGrouped = primary.size > 1 || secondary.size > 1
        val accepted = when {
            exact -> true
            isGrouped && similarity >= MIN_GROUP_TEXT_SIMILARITY && lengthBalance >= MIN_GROUP_LENGTH_BALANCE -> true
            similarity >= STRONG_TEXT_SIMILARITY && lengthBalance >= MIN_STRONG_LENGTH_BALANCE -> true
            similarity >= TIMED_TEXT_SIMILARITY && lengthBalance >= MIN_TIMED_LENGTH_BALANCE &&
                timingScore >= MIN_SUPPORTING_TIME_SCORE -> true
            else -> false
        }
        if (!accepted) return null

        val groupPenalty = GROUP_SIZE_PENALTY * (primary.size + secondary.size - 2)
        val confidence = (similarity * TEXT_WEIGHT + timingScore * TIME_WEIGHT - groupPenalty)
            .coerceIn(0.0, 1.0)
        val matchedCharacters = (minOf(primaryText.length, secondaryText.length) * similarity).roundToInt()
        val group = CrossSourceAlignmentGroup(
            primaryIndices = primary.map(IndexedValue<LyricLine>::index),
            secondaryIndices = secondary.map(IndexedValue<LyricLine>::index),
            primaryLines = primary.map(IndexedValue<LyricLine>::value),
            secondaryLines = secondary.map(IndexedValue<LyricLine>::value),
            textSimilarity = similarity,
            timingScore = timingScore,
            confidence = confidence,
            matchedCharacterCount = matchedCharacters,
        )
        val pathScore = MATCH_REWARD + similarity * TEXT_PATH_WEIGHT + timingScore * TIME_PATH_WEIGHT - groupPenalty
        return ScoredGroup(group, pathScore)
    }

    private fun groupBoundariesAreCompatible(lines: List<LyricLine>): Boolean {
        if (lines.size <= 1) return true
        val agents = lines.mapNotNull(LyricLine::agent).filter(String::isNotBlank).distinct()
        if (agents.size > 1) return false
        return lines.zipWithNext().none { (left, right) ->
            val leftStart = left.startMs
            val rightStart = right.startMs
            leftStart != null && rightStart != null && rightStart - leftStart > MAX_GROUP_GAP_MS
        }
    }

    private fun timingScore(
        primary: List<IndexedValue<LyricLine>>,
        secondary: List<IndexedValue<LyricLine>>,
        timeOffsetMs: Long,
    ): Double {
        val primaryStart = primary.firstNotNullOfOrNull { it.value.startMs } ?: return 0.0
        val secondaryStart = secondary.firstNotNullOfOrNull { it.value.startMs } ?: return 0.0
        val startScore = timeDistanceScore(abs(primaryStart + timeOffsetMs - secondaryStart))
        val primaryEnd = primary.asReversed().firstNotNullOfOrNull { it.value.effectiveEndMs() }
        val secondaryEnd = secondary.asReversed().firstNotNullOfOrNull { it.value.effectiveEndMs() }
        return if (primaryEnd != null && secondaryEnd != null) {
            (startScore + timeDistanceScore(abs(primaryEnd + timeOffsetMs - secondaryEnd))) / 2.0
        } else {
            startScore
        }
    }

    private fun timeDistanceScore(distanceMs: Long): Double = when (distanceMs) {
        in 0..EXACT_TIME_TOLERANCE_MS -> 1.0
        in (EXACT_TIME_TOLERANCE_MS + 1)..TIMED_TOLERANCE_MS -> 0.75
        in (TIMED_TOLERANCE_MS + 1)..LOOSE_TIME_TOLERANCE_MS -> 0.45
        else -> 0.0
    }

    private fun estimateTimeOffset(
        primary: List<IndexedValue<LyricLine>>,
        secondary: List<IndexedValue<LyricLine>>,
    ): Long {
        val primaryUnique = uniqueTimedLines(primary)
        val secondaryUnique = uniqueTimedLines(secondary)
        val offsets = primaryUnique.mapNotNull { (text, primaryLine) ->
            val secondaryLine = secondaryUnique[text] ?: return@mapNotNull null
            secondaryLine.value.startMs?.minus(primaryLine.value.startMs ?: return@mapNotNull null)
        }.sorted()
        if (offsets.isEmpty()) return 0L
        val middle = offsets.size / 2
        return if (offsets.size % 2 == 1) offsets[middle] else (offsets[middle - 1] + offsets[middle]) / 2
    }

    private fun uniqueTimedLines(lines: List<IndexedValue<LyricLine>>): Map<String, IndexedValue<LyricLine>> =
        lines.filter { it.value.startMs != null }
            .groupBy { TextNormalizer.compact(it.value.text) }
            .filter { (text, matches) -> text.length >= MIN_ANCHOR_CHARACTERS && matches.size == 1 }
            .mapValues { it.value.single() }

    private fun compactGroup(lines: List<IndexedValue<LyricLine>>): String =
        lines.joinToString("") { TextNormalizer.compact(it.value.text) }

    private data class ScoredGroup(
        val alignmentGroup: CrossSourceAlignmentGroup,
        val pathScore: Double,
    )

    private data class AlignmentStep(
        val primaryCount: Int,
        val secondaryCount: Int,
        val group: CrossSourceAlignmentGroup? = null,
    )

    private const val MAX_GROUP_LINES = 3
    private const val MIN_ANCHOR_CHARACTERS = 4
    private const val MAX_GROUP_GAP_MS = 10_000L
    private const val MIN_GROUP_TEXT_SIMILARITY = 0.90
    private const val MIN_GROUP_LENGTH_BALANCE = 0.72
    private const val STRONG_TEXT_SIMILARITY = 0.88
    private const val TIMED_TEXT_SIMILARITY = 0.76
    private const val MIN_STRONG_LENGTH_BALANCE = 0.65
    private const val MIN_TIMED_LENGTH_BALANCE = 0.75
    private const val MIN_SUPPORTING_TIME_SCORE = 0.75
    private const val EXACT_TIME_TOLERANCE_MS = 350L
    private const val TIMED_TOLERANCE_MS = 1_800L
    private const val LOOSE_TIME_TOLERANCE_MS = 3_000L
    private const val TEXT_WEIGHT = 0.85
    private const val TIME_WEIGHT = 0.15
    private const val MATCH_REWARD = 1.0
    private const val TEXT_PATH_WEIGHT = 2.0
    private const val TIME_PATH_WEIGHT = 0.25
    private const val GROUP_SIZE_PENALTY = 0.08
    private const val SKIP_PENALTY = -0.45
    private const val NEGATIVE_INFINITY = -1.0e12
}

internal enum class CrossSourceAlignmentRelation {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY,
}

internal data class CrossSourceAlignmentGroup(
    val primaryIndices: List<Int>,
    val secondaryIndices: List<Int>,
    val primaryLines: List<LyricLine>,
    val secondaryLines: List<LyricLine>,
    val textSimilarity: Double,
    val timingScore: Double,
    val confidence: Double,
    val matchedCharacterCount: Int,
) {
    val relation: CrossSourceAlignmentRelation
        get() = when {
            primaryIndices.size == 1 && secondaryIndices.size == 1 -> CrossSourceAlignmentRelation.ONE_TO_ONE
            primaryIndices.size == 1 -> CrossSourceAlignmentRelation.ONE_TO_MANY
            secondaryIndices.size == 1 -> CrossSourceAlignmentRelation.MANY_TO_ONE
            else -> CrossSourceAlignmentRelation.MANY_TO_MANY
        }
}

internal data class CrossSourceAlignment(
    val groups: List<CrossSourceAlignmentGroup>,
    val primaryLineCount: Int,
    val secondaryLineCount: Int,
    val primaryCharacterCount: Int,
    val secondaryCharacterCount: Int,
) {
    val matchedCharacterCoverage: Double
        get() {
            val comparable = minOf(primaryCharacterCount, secondaryCharacterCount)
            if (comparable <= 0) return 0.0
            return groups.sumOf(CrossSourceAlignmentGroup::matchedCharacterCount).toDouble() / comparable
        }

    val provesSameRecording: Boolean
        get() {
            if (groups.size < MIN_RECORDING_EVIDENCE_GROUPS) return false
            if (matchedCharacterCoverage < MIN_RECORDING_CHARACTER_COVERAGE) return false
            val evidenceCharacters = groups.sumOf(CrossSourceAlignmentGroup::matchedCharacterCount)
            if (evidenceCharacters <= 0) return false
            val weightedSimilarity = groups.sumOf { it.textSimilarity * it.matchedCharacterCount } / evidenceCharacters
            return weightedSimilarity >= MIN_AVERAGE_SIMILARITY
        }

    private companion object {
        const val MIN_RECORDING_EVIDENCE_GROUPS = 3
        const val MIN_RECORDING_CHARACTER_COVERAGE = 0.60
        const val MIN_AVERAGE_SIMILARITY = 0.84
    }
}
