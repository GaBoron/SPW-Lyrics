package dev.gaboron.spwlyrics.integration.manualui

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal class ManualUiActivationSignal {
    private val pending = ArrayBlockingQueue<String>(1)

    fun request(mode: String) {
        pending.poll()
        pending.offer(mode)
    }

    fun await(timeout: Duration): String? = pending.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
}
