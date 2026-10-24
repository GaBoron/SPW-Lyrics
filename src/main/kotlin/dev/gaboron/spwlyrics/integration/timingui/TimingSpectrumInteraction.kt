package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import dev.gaboron.spwlyrics.domain.TimingCursor
import kotlin.math.*

internal enum class SpectrumHandle { MOVE, START, END }
internal data class SpectrumDrag(val word: SpectralWord, val handle: SpectrumHandle) {
    fun at(delta: Long, duration: Long): SpectralWord {
        val maximum = duration.takeIf { it > 0 } ?: Long.MAX_VALUE / 2
        val from = word.start.coerceIn(0, maximum - 1)
        val to = word.end.coerceIn(from + 1, maximum)
        return when (handle) {
            SpectrumHandle.MOVE -> {
                val length = (word.end - word.start).coerceIn(1, maximum)
                val start = (word.start + delta).coerceIn(0, maximum - length)
                word.copy(start = start, end = start + length)
            }
            SpectrumHandle.START -> word.copy(start = (word.start + delta).coerceIn(0, to - 1), end = to)
            SpectrumHandle.END -> word.copy(start = from, end = (word.end + delta).coerceIn(from + 1, maximum))
        }
    }
}

/** Interval lanes keep overlapping voices individually selectable. */
internal object SpectrumWordLayout {
    fun lanes(words: List<SpectralWord>): List<SpectralWord> {
        val ends = mutableListOf<Long>()
        return words.sortedBy { it.start }.map { word ->
            val lane = ends.indexOfFirst { it <= word.start }.takeIf { it >= 0 } ?: ends.size.also { ends.add(0) }
            ends[lane] = word.end
            word.copy(lane = lane)
        }
    }
    fun hit(words: List<SpectralWord>, time: Long, lane: Int?, selected: TimingCursor?, tolerance: Long): SpectrumDrag? {
        val candidates = words.filter { time >= it.start - tolerance && time <= it.end + tolerance }
        val matching = lane?.let { row -> candidates.filter { it.lane == row } }?.takeIf { it.isNotEmpty() } ?: candidates
        val word = matching.firstOrNull { it.cursor == selected } ?: matching.minByOrNull {
            if (time in it.start..it.end) 0 else minOf(abs(time - it.start), abs(time - it.end))
        } ?: return null
        val edge = minOf(tolerance, (word.end - word.start) / 4)
        val handle = when {
            abs(time - word.start) <= edge -> SpectrumHandle.START
            abs(time - word.end) <= edge -> SpectrumHandle.END
            else -> SpectrumHandle.MOVE
        }
        return SpectrumDrag(word, handle)
    }
}

/** Preview during a gesture, commit exactly once on release, discard on cancellation. */
@Composable
internal fun Modifier.spectrumWordInput(start: Long, span: Long, words: List<SpectralWord>, selected: TimingCursor?,
    laneHeight: Float, duration: Long, preview: (SpectralWord?) -> Unit, edit: (SpectralWord) -> Unit, seek: (Long) -> Unit): Modifier {
    val currentWords by rememberUpdatedState(words)
    val currentSelected by rememberUpdatedState(selected)
    val currentEdit by rememberUpdatedState(edit)
    val currentSeek by rememberUpdatedState(seek)
    return pointerInput(start, span, laneHeight, duration) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val origin = down.position
            fun time(x: Float) = start + (x / size.width * span).roundToLong()
            val lane = (origin.y / laneHeight).toInt().takeIf { origin.y < laneHeight * (currentWords.maxOfOrNull { it.lane + 1 } ?: 1) }
            val target = SpectrumWordLayout.hit(currentWords, time(origin.x), lane, currentSelected, (7f / size.width * span).roundToLong())
            var dragging = false
            var changed: SpectralWord? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (pointer.isConsumed && !dragging) break
                    val dx = pointer.position.x - origin.x
                    val slop = if (down.type == PointerType.Mouse) 2f else viewConfiguration.touchSlop
                    if (!dragging && target != null && abs(dx) > slop) dragging = true
                    if (dragging && target != null) {
                        pointer.consume()
                        changed = target.at((dx / size.width * span).roundToLong(), duration)
                        preview(changed)
                    }
                    if (!pointer.pressed) {
                        if (dragging) changed?.let(currentEdit) else currentSeek(time(pointer.position.x).coerceAtLeast(0))
                        break
                    }
                }
            } finally { preview(null) }
        }
    }
}
