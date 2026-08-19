package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TextNormalizer
import kotlin.math.abs
import kotlin.math.ceil

/** Aligns two providers' primary lyric lines before consuming secondary text from either document. */
internal object CrossSourceLyricsAligner {
    fun align(primary: LyricsDocument, secondary: LyricsDocument): CrossSourceAlignment {
        val primaryLines = primary.lines.withIndex()
            .filter { (_, line) -> !line.background && line.text.isNotBlank() }
        val secondaryLines = secondary.lines.withIndex()
            .filter { (_, line) -> !line.background && line.text.isNotBlank() }
        if (primaryLines.isEmpty() || secondaryLines.isEmpty()) {
            return CrossSourceAlignment(emptyList(), primaryLines.size, secondaryLines.size)
        }

        val scores = Array(primaryLines.size) { primaryIndex ->
            Array<LineScore?>(secondaryLines.size) { secondaryIndex ->
                score(primaryLines[primaryIndex].value, secondaryLines[secondaryIndex].value)
            }
        }
        val totals = Array(primaryLines.size + 1) { DoubleArray(secondaryLines.size + 1) }
        val moves = Array(primaryLines.size + 1) { Array(secondaryLines.size + 1) { Move.NONE } }

        for (primaryIndex in 1..primaryLines.size) {
            for (secondaryIndex in 1..secondaryLines.size) {
                var best = totals[primaryIndex - 1][secondaryIndex]
                var move = Move.SKIP_PRIMARY
                if (totals[primaryIndex][secondaryIndex - 1] > best) {
                    best = totals[primaryIndex][secondaryIndex - 1]
                    move = Move.SKIP_SECONDARY
                }
                scores[primaryIndex - 1][secondaryIndex - 1]?.let { lineScore ->
                    val matched = totals[primaryIndex - 1][secondaryIndex - 1] + lineScore.value
                    if (matched >= best) {
                        best = matched
                        move = Move.MATCH
                    }
                }
                totals[primaryIndex][secondaryIndex] = best
                moves[primaryIndex][secondaryIndex] = move
            }
        }

        val matches = mutableListOf<CrossSourceLineMatch>()
        var primaryIndex = primaryLines.size
        var secondaryIndex = secondaryLines.size
        while (primaryIndex > 0 && secondaryIndex > 0) {
            when (moves[primaryIndex][secondaryIndex]) {
                Move.MATCH -> {
                    val lineScore = scores[primaryIndex - 1][secondaryIndex - 1]!!
                    val primaryLine = primaryLines[primaryIndex - 1]
                    val secondaryLine = secondaryLines[secondaryIndex - 1]
                    matches += CrossSourceLineMatch(
                        primaryIndex = primaryLine.index,
                        secondaryIndex = secondaryLine.index,
                        secondaryLine = secondaryLine.value,
                        textSimilarity = lineScore.textSimilarity,
                    )
                    primaryIndex--
                    secondaryIndex--
                }
                Move.SKIP_PRIMARY -> primaryIndex--
                Move.SKIP_SECONDARY -> secondaryIndex--
                Move.NONE -> break
            }
        }
        return CrossSourceAlignment(matches.reversed(), primaryLines.size, secondaryLines.size)
    }

    private fun score(primary: LyricLine, secondary: LyricLine): LineScore? {
        val primaryText = TextNormalizer.compact(primary.text)
        val secondaryText = TextNormalizer.compact(secondary.text)
        if (primaryText.isEmpty() || secondaryText.isEmpty()) return null

        val similarity = TextNormalizer.similarity(primary.text, secondary.text)
        val lengthBalance = minOf(primaryText.length, secondaryText.length).toDouble() /
            maxOf(primaryText.length, secondaryText.length)
        val distance = startDistance(primary, secondary)
        val accepted = when {
            similarity >= STRONG_TEXT_SIMILARITY && lengthBalance >= MIN_STRONG_LENGTH_BALANCE -> true
            similarity >= TIMED_TEXT_SIMILARITY && lengthBalance >= MIN_TIMED_LENGTH_BALANCE &&
                distance != null && distance <= TIMED_TOLERANCE_MS -> true
            similarity >= EXACT_TIME_TEXT_SIMILARITY && lengthBalance >= MIN_EXACT_TIME_LENGTH_BALANCE &&
                distance != null && distance <= EXACT_TIME_TOLERANCE_MS -> true
            else -> false
        }
        if (!accepted) return null

        val timing = when (distance) {
            null -> 0.0
            in 0..EXACT_TIME_TOLERANCE_MS -> 1.0
            in (EXACT_TIME_TOLERANCE_MS + 1)..TIMED_TOLERANCE_MS -> 0.6
            else -> 0.0
        }
        return LineScore(similarity * TEXT_WEIGHT + timing * TIME_WEIGHT, similarity)
    }

    private fun startDistance(primary: LyricLine, secondary: LyricLine): Long? {
        val primaryStart = primary.startMs ?: return null
        val secondaryStart = secondary.startMs ?: return null
        return abs(primaryStart - secondaryStart)
    }

    private data class LineScore(val value: Double, val textSimilarity: Double)

    private enum class Move { NONE, MATCH, SKIP_PRIMARY, SKIP_SECONDARY }

    private const val STRONG_TEXT_SIMILARITY = 0.88
    private const val TIMED_TEXT_SIMILARITY = 0.72
    private const val EXACT_TIME_TEXT_SIMILARITY = 0.58
    private const val MIN_STRONG_LENGTH_BALANCE = 0.65
    private const val MIN_TIMED_LENGTH_BALANCE = 0.72
    private const val MIN_EXACT_TIME_LENGTH_BALANCE = 0.80
    private const val EXACT_TIME_TOLERANCE_MS = 350L
    private const val TIMED_TOLERANCE_MS = 1_800L
    private const val TEXT_WEIGHT = 0.85
    private const val TIME_WEIGHT = 0.15
}

internal data class CrossSourceLineMatch(
    val primaryIndex: Int,
    val secondaryIndex: Int,
    val secondaryLine: LyricLine,
    val textSimilarity: Double,
)

internal data class CrossSourceAlignment(
    val matches: List<CrossSourceLineMatch>,
    val primaryLineCount: Int,
    val secondaryLineCount: Int,
) {
    val provesSameRecording: Boolean
        get() {
            val comparable = minOf(primaryLineCount, secondaryLineCount)
            if (comparable < MIN_RECORDING_EVIDENCE_LINES) return false
            val required = maxOf(
                MIN_RECORDING_EVIDENCE_LINES,
                ceil(comparable * MIN_RECORDING_COVERAGE).toInt(),
            )
            return matches.size >= required && matches.map(CrossSourceLineMatch::textSimilarity).average() >= MIN_AVERAGE_SIMILARITY
        }

    private companion object {
        const val MIN_RECORDING_EVIDENCE_LINES = 3
        const val MIN_RECORDING_COVERAGE = 0.45
        const val MIN_AVERAGE_SIMILARITY = 0.78
    }
}
