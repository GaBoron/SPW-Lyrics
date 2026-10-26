package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.LyricRowOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun rememberMetadataLineReorder(controller: MetadataEditorController, list: LazyListState): MetadataLineReorder {
    val latest = rememberUpdatedState(controller.state)
    val scope = rememberCoroutineScope()
    val reorder = remember(controller, list) { MetadataLineReorder(list, scope, { latest.value }, controller::moveLine) }
    LaunchedEffect(latest.value.structured, latest.value.validating) { reorder.reconcile() }
    LaunchedEffect(reorder.draggingId) {
        while (reorder.draggingId != null && isActive) {
            val viewport = list.layoutInfo
            val edge = 48
            val delta = when {
                reorder.center < viewport.viewportStartOffset + edge -> -12f
                reorder.center > viewport.viewportEndOffset - edge -> 12f
                else -> 0f
            }
            if (delta != 0f) { list.scrollBy(delta); reorder.updateDrop() }
            withFrameNanos { }
        }
    }
    DisposableEffect(reorder) { onDispose { reorder.dispose() } }
    return reorder
}

/** Keeps the preview order separate from the TTML transaction and settles the lifted row before releasing it. */
@Stable
internal class MetadataLineReorder(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val state: () -> MetadataEditorState,
    private val commit: (Int, Int, Boolean) -> Unit,
) {
    var draggingId by mutableStateOf<Int?>(null)
        private set
    var settlingId by mutableStateOf<Int?>(null)
        private set
    var center by mutableFloatStateOf(0f)
        private set
    private var order by mutableStateOf<List<Int>?>(null)
    private val settlingOffset = Animatable(0f)
    private var settle: Job? = null
    private var drop: Pair<Int, Boolean>? = null
    private var group = emptySet<Int>()
    val active get() = draggingId != null || settlingId != null

    private fun originalRows(): List<EditableLyricLine> {
        val snapshot = state()
        return snapshot.structured?.lines.orEmpty().map { snapshot.lineEdits[it.id] ?: it }
    }

    fun rows(): List<EditableLyricLine> {
        val rows = originalRows()
        val byId = rows.associateBy { it.id }
        return order?.takeIf { it.size == rows.size && it.toSet() == byId.keys }?.map { byId.getValue(it) } ?: rows
    }

    fun start(id: Int) {
        if (active || !state().canEditRows || state().validating) return
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val rows = originalRows()
        order = rows.map { it.id }
        group = LyricRowOrder.group(rows, id)
        center = item.offset + item.size / 2f
        drop = null
        draggingId = id
    }

    fun move(delta: Float) {
        if (draggingId == null) return
        center += delta
        updateDrop()
    }

    fun updateDrop() {
        val id = draggingId ?: return
        val visible = list.layoutInfo.visibleItemsInfo
        val target = visible.firstOrNull { center >= it.offset && center <= it.offset + it.size }
            ?: visible.minByOrNull { kotlin.math.abs(center - it.offset - it.size / 2f) } ?: return
        val targetId = target.key as? Int ?: return
        if (targetId in group) return
        val after = center > target.offset + target.size / 2f
        drop = targetId to after
        runCatching { LyricRowOrder.move(rows(), id, targetId, after) }.getOrNull()?.let { next ->
            if (next != order) {
                val first = list.firstVisibleItemIndex
                val offset = list.firstVisibleItemScrollOffset
                order = next
                list.requestScrollToItem(first, offset)
            }
        }
    }

    fun offset(id: Int): Float = when (id) {
        draggingId -> list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            ?.let { center - it.offset - it.size / 2f } ?: 0f
        settlingId -> settlingOffset.value
        else -> 0f
    }

    fun finish(cancelled: Boolean = false) {
        val id = draggingId ?: return
        val initialOffset = offset(id)
        val destination = drop
        val changed = order != originalRows().map { it.id }
        settle = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settlingOffset.snapTo(initialOffset)
            settlingId = id
            draggingId = null
            if (!cancelled && changed && destination != null) commit(id, destination.first, destination.second)
            settlingOffset.animateTo(0f, spring(stiffness = 650f, dampingRatio = 1f))
            settlingId = null
            reconcile()
        }
    }

    fun reconcile() {
        if (!active && !state().validating) order = null
    }

    fun dispose() { settle?.cancel() }
}
