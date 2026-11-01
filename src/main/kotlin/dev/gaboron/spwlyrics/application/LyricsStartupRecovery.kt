package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Replays a missed startup load through the host, preserving its local-lyrics and replacement flow. */
internal class LyricsStartupRecovery(
    private val worker: ScheduledExecutorService,
    private val currentTrack: () -> TrackQuery?,
    private val bridge: LyricsRefreshBridge,
    private val callbacksReady: () -> Boolean,
) : AutoCloseable {
    private var pending: ScheduledFuture<*>? = null
    private var loadedTrack: TrackQuery? = null
    private var attempts = 0
    @Volatile private var closed = false

    /** Start only after the runtime is published and able to receive the replayed callback. */
    @Synchronized
    fun start() {
        if (closed || pending != null) return
        pending = worker.scheduleWithFixedDelay(::recover, RETRY_DELAY_MILLIS, RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun beforeLoadCompleted(query: TrackQuery) {
        if (!closed) loadedTrack = query
    }

    private fun recover() {
        if (closed) return
        val current = runCatching(currentTrack).getOrNull()
        val alreadyLoaded = synchronized(this) {
            if (closed) return
            attempts++
            current != null && loadedTrack?.let { loaded ->
                loaded.path == current.path && loaded.trackId == current.trackId
            } == true
        }
        if (alreadyLoaded) {
            close()
            return
        }
        val ready = runCatching(callbacksReady).getOrDefault(false)
        val reloaded = current != null && !closed && runCatching(bridge::reloadCurrentLyrics).getOrDefault(false)
        // Another extension may supply lyrics before ours is called. Once our extension is available,
        // a verified host load is enough; replaying until our own callback arrives would loop.
        if ((ready && reloaded) || attempts >= MAX_ATTEMPTS) close()
    }

    @Synchronized
    override fun close() {
        closed = true
        pending?.cancel(false)
        pending = null
        loadedTrack = null
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 500L
        const val MAX_ATTEMPTS = 60
    }
}
