package dev.gaboron.spwlyrics.integration.manualui

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal class ManualUiActivationSignal {
    private val pending = ArrayBlockingQueue<Unit>(1)

    fun request() {
        pending.offer(Unit)
    }

    fun await(timeout: Duration): Boolean =
        pending.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) != null
}
