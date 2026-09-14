package dev.gaboron.spwlyrics.application

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Prevents repeated cache callbacks from starting the same failed background lookup in a tight loop. */
internal class TranslationAttemptGate(
    private val retryIntervalNanos: Long = TimeUnit.SECONDS.toNanos(DEFAULT_RETRY_INTERVAL_SECONDS),
    private val nanoTime: () -> Long = System::nanoTime,
) {
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

    private companion object {
        const val DEFAULT_RETRY_INTERVAL_SECONDS = 30L
    }
}
