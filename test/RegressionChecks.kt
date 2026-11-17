package dev.gaboron.spwlyrics.regression

import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import dev.gaboron.spwlyrics.application.BackgroundAttemptGate
import dev.gaboron.spwlyrics.application.LyricsLoadPhase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object RegressionChecks {
    @JvmStatic fun main(args: Array<String>) {
        replacementPolicy()
        backgroundRetry()
        concurrentCallbacks()
        println("Replacement phase policy and background retry checks passed")
    }

    private fun replacementPolicy() {
        val before = LyricsLoadPhase.BEFORE_LOCAL
        val missing = LyricsLoadPhase.AFTER_LOCAL_MISSING
        check(AutomaticReplacementPolicy.ALWAYS.allowsAutomaticLoad(before))
        check(!AutomaticReplacementPolicy.ALWAYS.allowsAutomaticLoad(missing))
        check(!AutomaticReplacementPolicy.WHEN_LOCAL_MISSING.allowsAutomaticLoad(before))
        check(AutomaticReplacementPolicy.WHEN_LOCAL_MISSING.allowsAutomaticLoad(missing))
        check(LyricsLoadPhase.entries.none { AutomaticReplacementPolicy.MANUAL_ONLY.allowsAutomaticLoad(it) })
        for (policy in AutomaticReplacementPolicy.entries) {
            check(AutomaticReplacementPolicy.fromConfigValue(policy.configValue) == policy)
        }
        check(AutomaticReplacementPolicy.fromConfigValue("unknown") == AutomaticReplacementPolicy.ALWAYS)
    }

    private fun backgroundRetry() {
        var now = 0L
        val gate = BackgroundAttemptGate(2) { now }
        check(gate.allow("A", false))
        now = 1_999_999_999L
        check(!gate.allow("A", false))
        check(gate.allow("B", false))
        now = 2_000_000_000L
        check(gate.allow("A", false)) { "Retry must open at the interval boundary" }
        now += 1
        check(gate.allow("A", true))
        check(!gate.allow("A", false)) { "Forced attempts must also reset the cooldown" }
        gate.clear("A")
        check(gate.allow("A", false))
        check(!gate.allow("B", false)) { "Clearing one track must not clear another" }
    }

    private fun concurrentCallbacks() {
        val gate = BackgroundAttemptGate(60) { 0L }
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (0 until 32).map {
                workers.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    gate.allow("same-track", false)
                }
            }
            start.countDown()
            check(results.count { it.get(5, TimeUnit.SECONDS) } == 1) {
                "Concurrent callbacks started duplicate background work"
            }
        } finally {
            workers.shutdownNow()
        }
    }
}
