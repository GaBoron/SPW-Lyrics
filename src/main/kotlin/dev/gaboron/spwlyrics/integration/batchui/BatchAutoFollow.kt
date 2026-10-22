package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.application.LyricsBatchState
import kotlinx.coroutines.delay

/** A monotonic quiet period, independent of Compose frames and snapshot polling. */
internal class BatchFollowPause {
    private var lastInput: Long? = null
    fun userInput(now: Long) { lastInput = now }
    fun remainingNanos(now: Long): Long = lastInput?.let { (3_000_000_000L - (now - it)).coerceIn(0, 3_000_000_000L) } ?: 0
}

internal data class BatchListFollowing(val modifier: Modifier, val userInput: () -> Unit)

/** Observe genuine input, never isScrollInProgress: our own animation must not pause following. */
@Composable
internal fun rememberBatchAutoFollow(state: BatchProcessingState, list: LazyListState,
    scrollbar: MutableInteractionSource): BatchListFollowing {
    val pause = remember { BatchFollowPause() }
    var inputEpoch by remember { mutableLongStateOf(0) }
    var held by remember { mutableStateOf(false) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    val scrollbarDragged by scrollbar.collectIsDraggedAsState()
    val userInput = remember(pause) { { pause.userInput(System.nanoTime()); inputEpoch++; Unit } }
    val target = if (state.snapshot.state == LyricsBatchState.RUNNING)
        state.snapshot.items.indexOfLast { it.state == LyricsBatchItemState.SEARCHING } else -1
    LaunchedEffect(target, inputEpoch, held, dragged, scrollbarDragged) {
        if (target < 0 || held || dragged || scrollbarDragged) return@LaunchedEffect
        val remaining = pause.remainingNanos(System.nanoTime())
        if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
        val layout = list.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible == null || visible.offset < layout.viewportStartOffset || visible.offset + visible.size > layout.viewportEndOffset)
            list.animateScrollToItem((target - 2).coerceAtLeast(0))
    }
    val observe = Modifier.pointerInput(pause) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    held = event.changes.any { it.pressed }
                    if (event.type == PointerEventType.Scroll || event.type == PointerEventType.Press || event.type == PointerEventType.Release
                        || event.changes.any { it.pressed && it.position != it.previousPosition }) userInput()
                }
            }
        } finally { held = false }
    }
    return BatchListFollowing(observe, userInput)
}
