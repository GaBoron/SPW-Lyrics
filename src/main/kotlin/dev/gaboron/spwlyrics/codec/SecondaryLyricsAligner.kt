package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricLine
import kotlin.math.abs

internal object SecondaryLyricsAligner {
    fun align(
        originals: List<LyricLine>,
        secondary: List<LyricLine>,
        toleranceMs: Long,
    ): List<LyricLine?> {
        val usable = secondary.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return List(originals.size) { null }

        val timed = usable.filter { it.startMs != null }.sortedBy { it.startMs }
        if (timed.isEmpty()) {
            return if (usable.size == originals.size) usable.map { it } else List(originals.size) { null }
        }

        var cursor = 0
        return originals.map { original ->
            val start = original.startMs ?: return@map null
            while (cursor < timed.size && timed[cursor].startMs!! < start - toleranceMs) cursor++

            var bestIndex = -1
            var bestDistance = Long.MAX_VALUE
            var index = cursor
            while (index < timed.size) {
                val candidateStart = timed[index].startMs!!
                if (candidateStart > start + toleranceMs) break
                val distance = abs(candidateStart - start)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
                index++
            }
            if (bestIndex < 0) null else timed[bestIndex].also { cursor = bestIndex + 1 }
        }
    }
}
