package dev.gaboron.spwlyrics.application

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Prevents repeated playback callbacks from starting the same background operation in a tight loop. */
internal class BackgroundAttemptGate(
    retryIntervalSeconds: Long,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val retryIntervalNanos = TimeUnit.SECONDS.toNanos(retryIntervalSeconds)
    private val lastAttempts = ConcurrentHashMap<String, Long>()

    fun allow(key: String, force: Boolean): Boolean {
        val now = nanoTime()
        var allowed = false
        lastAttempts.compute(key) { _, previous ->
            if (force || previous == null || now - previous >= retryIntervalNanos) {
                allowed = true
                now
            } else {
                previous
            }
        }
        return allowed
    }

    fun clear(key: String) {
        lastAttempts.remove(key)
    }
}
