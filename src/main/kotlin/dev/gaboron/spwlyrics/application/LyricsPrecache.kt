package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledThreadPoolExecutor
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

/** A bounded lookahead in library order; the host API does not expose its playback queue. */
internal class LyricsPrecache(
    private val loadLibrary: () -> List<TrackQuery>,
    private val prepare: (TrackQuery) -> Future<*>?,
    private val enabled: () -> Boolean,
    private val performance: PluginPerformance = PluginPerformance(),
) : AutoCloseable {
    private val worker = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "spw-lyrics-precache").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private var scheduled: Future<*>? = null
    private var currentKey: String? = null
    @Volatile private var revision = 0L
    @Volatile private var closed = false
    private var library = emptyList<TrackQuery>()
    private var libraryLoadedAt = 0L

    @Synchronized
    fun trackChanged(query: TrackQuery, force: Boolean = false) = performance.measure(PluginPerformance.Operation.PRECACHE_CALLBACK) {
        if (closed || (!force && currentKey == query.key)) return@measure
        currentKey = query.key
        val expected = ++revision
        scheduled?.cancel(true)
        if (!enabled()) return@measure
        scheduled = worker.schedule({
            performance.measure(PluginPerformance.Operation.PRECACHE_WORK) { prepareAhead(query, expected) }
        }, 750, TimeUnit.MILLISECONDS)
    }

    private fun prepareAhead(query: TrackQuery, expected: Long) {
        fun active() = !closed && revision == expected && enabled() && !Thread.currentThread().isInterrupted
        try {
            if (!active()) return
            val now = System.nanoTime()
            if (library.isEmpty() || now - libraryLoadedAt > TimeUnit.MINUTES.toNanos(5)) {
                library = loadLibrary().distinctBy { it.key }
                libraryLoadedAt = now
            }
            val index = library.indexOfFirst { it.key == query.key }
            if (index < 0) return
            val upcoming = (1..minOf(LOOKAHEAD, library.size - 1)).map { library[(index + it) % library.size] }
            for (next in upcoming) {
                if (!active()) return
                prepare(next)?.get()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // Permission failures and speculative search failures must not interrupt playback.
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        revision++
        scheduled?.cancel(true)
        worker.shutdownNow()
    }

    private companion object { const val LOOKAHEAD = 3 }
}
