package dev.gaboron.spwlyrics.integration.manualui

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualUiActivationSignalTest {
    @Test
    fun `delivers one pending activation request`() {
        val signal = ManualUiActivationSignal()

        signal.request()

        assertTrue(signal.await(Duration.ZERO))
        assertFalse(signal.await(Duration.ZERO))
    }

    @Test
    fun `coalesces repeated activation requests`() {
        val signal = ManualUiActivationSignal()

        signal.request()
        signal.request()

        assertTrue(signal.await(Duration.ZERO))
        assertFalse(signal.await(Duration.ZERO))
    }

    @Test
    fun `wakes an existing activation waiter`() {
        val signal = ManualUiActivationSignal()
        val waiting = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<Boolean> {
            waiting.countDown()
            signal.await(Duration.ofSeconds(1))
        }

        assertTrue(waiting.await(1, TimeUnit.SECONDS))
        signal.request()

        assertTrue(result.get(1, TimeUnit.SECONDS))
        executor.shutdownNow()
    }
}
