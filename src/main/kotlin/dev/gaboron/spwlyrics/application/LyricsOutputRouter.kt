package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Implementations are called exclusively on the output worker, never in a playback/UI callback. */
interface NativeTtmlReceiver : AutoCloseable {
    /** Null means unavailable; the token changes when the receiver is registered again. */
    fun state(): Any?
    fun submit(query: TrackQuery, rawTtml: String, offsetMs: Long): Boolean
    fun clear(query: TrackQuery)
    override fun close() {}
}

/** One current-song output slot. Until native acceptance is confirmed, ordinary output stays usable. */
class LyricsOutputRouter(
    private val receiver: NativeTtmlReceiver,
    private val isCurrent: (TrackQuery) -> Boolean,
    private val refresh: (TrackQuery) -> Unit,
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "spw-lyrics-output").apply { isDaemon = true }
    },
) : AutoCloseable {
    private enum class Delivery { PENDING, NATIVE, FALLBACK }
    private data class Selection(val query: TrackQuery, val document: LyricsDocument, val delayMs: Int)

    private val lock = Any()
    private var selection: Selection? = null
    private var delivery = Delivery.PENDING
    private var revision = 0L
    private var processedRevision = -1L
    private var receiverState: Any? = null
    private var fallback: String? = null
    private var closed = false

    init {
        worker.scheduleWithFixedDelay(::reconcileSafely, 500, 500, TimeUnit.MILLISECONDS)
    }

    fun render(query: TrackQuery, document: LyricsDocument, delayMs: Int, encoded: String? = null): String? {
        val expected = Selection(query, document, delayMs)
        offer(query, document, delayMs, encoded)
        synchronized(lock) {
            if (closed || selection != expected || delivery == Delivery.NATIVE) return null
            fallback?.let { return it }
        }
        val result = SpwLyricsEncoder.encode(document, delayMs)
        return synchronized(lock) {
            if (closed || selection != expected || delivery == Delivery.NATIVE) null
            else result.also { fallback = it }
        }
    }

    /** A newly fetched result can negotiate native output before the host requests its lyrics. */
    fun offer(query: TrackQuery, document: LyricsDocument, delayMs: Int, encoded: String? = null) {
        val expected = Selection(query, document, delayMs)
        synchronized(lock) {
            if (closed) return
            if (selection != expected) {
                selection = expected
                revision++
                delivery = if (document.rawTtml == null) Delivery.FALLBACK else Delivery.PENDING
                fallback = if (delayMs == 0) encoded else null
                worker.execute(::reconcileSafely)
            }
        }
    }

    fun setDelay(query: TrackQuery, delayMs: Int) {
        val selected = synchronized(lock) { selection?.takeIf { sameTrack(it.query, query) } } ?: return
        offer(query, selected.document, delayMs)
    }

    /** Explicit handback also invalidates any submit already being parsed by the receiver. */
    fun withdraw(query: TrackQuery) {
        synchronized(lock) {
            if (closed) return
            selection = null
            fallback = null
            revision++
            worker.execute { runCatching { receiver.clear(query) } }
        }
    }

    private fun reconcileSafely() {
        runCatching { reconcile() }
    }

    private fun reconcile() {
        val (selected, expectedRevision) = synchronized(lock) {
            if (closed) return
            (selection ?: return) to revision
        }
        if (!current(selected.query)) {
            runCatching { receiver.clear(selected.query) }
            synchronized(lock) {
                if (revision == expectedRevision) { selection = null; revision++ }
            }
            return
        }
        val state = runCatching(receiver::state).getOrNull()
        synchronized(lock) {
            if (closed || revision != expectedRevision) return
            if (processedRevision == expectedRevision && receiverState == state) return
        }
        val raw = selected.document.rawTtml
        val accepted = raw != null && state != null && runCatching {
            receiver.submit(selected.query, raw, selected.delayMs.toLong())
        }.getOrDefault(false)
        if (!accepted) runCatching { receiver.clear(selected.query) }
        val stillCurrent = current(selected.query)
        val shouldRefresh = synchronized(lock) {
            if (closed || revision != expectedRevision || !stillCurrent) {
                false
            } else {
                val next = if (accepted) Delivery.NATIVE else Delivery.FALLBACK
                val changed = delivery != next
                delivery = next
                processedRevision = expectedRevision
                receiverState = state
                changed
            }
        }
        // Submissions and clears share this worker: a superseded result cannot clear a newer submit.
        if (accepted && synchronized(lock) { closed || revision != expectedRevision || !stillCurrent }) {
            runCatching { receiver.clear(selected.query) }
        }
        if (shouldRefresh && current(selected.query)) refresh(selected.query)
    }

    private fun current(query: TrackQuery): Boolean = runCatching { isCurrent(query) }.getOrDefault(false)

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            val previous = selection
            selection = null
            revision++
            worker.execute {
                previous?.let { runCatching { receiver.clear(it.query) } }
                runCatching(receiver::close)
            }
            worker.shutdown()
        }
    }

    private fun sameTrack(left: TrackQuery, right: TrackQuery): Boolean =
        left.path == right.path && (left.trackId.isBlank() || right.trackId.isBlank() || left.trackId == right.trackId)
}
