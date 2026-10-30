package dev.gaboron.spwlyrics.diagnostics

import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/** Per-enable counters: no paths, song metadata, or per-callback log output. */
class PluginPerformance {
    private val sessionId = sequence.incrementAndGet()
    enum class Operation {
        START, STORAGE, STORAGE_PREPARE, CONFIG, HTTP, PROVIDERS,
        AMLL, QQ, KUGOU, NETEASE, SODA, APPLE, LRCSHARE, MUSICBRAINZ, LOCAL,
        RESOLVER, COORDINATOR, WORKSPACE, HOST_START,
        HOST_CACHE_READ, HOST_CACHE_INSTALL, HOST_CACHE_FALLBACK, HOST_SCAN, HOST_PREPARE, HOST_INSTALL, HOST_READY,
        BEFORE_LOAD, AFTER_MISSING, PRECACHE_CALLBACK, PRECACHE_WORK,
        CONFIG_CALLBACK, CONFIG_RELOAD, SEARCH_QUEUE, SEARCH, DECODE, MEDIA_CLEANUP, STOP,
    }

    private class Samples {
        val count = LongAdder()
        val nanos = LongAdder()
        val maximum = AtomicLong()
        val onUi = LongAdder()
    }

    private val samples = Array(Operation.entries.size) { Samples() }
    private val pendingPeak = AtomicLong()
    private val cancelled = LongAdder()

    fun <T> measure(operation: Operation, action: () -> T): T {
        val started = System.nanoTime()
        try {
            return action()
        } finally {
            record(operation, System.nanoTime() - started)
        }
    }

    fun record(operation: Operation, nanos: Long) {
        val target = samples[operation.ordinal]
        target.count.increment()
        target.nanos.add(nanos)
        target.maximum.accumulateAndGet(nanos, ::maxOf)
        if (EventQueue.isDispatchThread()) target.onUi.increment()
    }

    fun pending(count: Int) { pendingPeak.accumulateAndGet(count.toLong(), ::maxOf) }
    fun cancelled() { cancelled.increment() }

    fun report(reason: String) {
        val summary = Operation.entries.mapNotNull { operation ->
            val target = samples[operation.ordinal]
            val count = target.count.sum()
            if (count == 0L) null else {
                val averageUs = target.nanos.sum() / count / 1000
                "${operation.name}=${count}x/avg ${averageUs}us/max ${target.maximum.get() / 1000}us/ui ${target.onUi.sum()}"
            }
        }.joinToString("; ")
        System.getLogger("SPW Lyrics").log(System.Logger.Level.INFO,
            "性能统计 [session=$sessionId/$reason] $summary; pendingPeak=${pendingPeak.get()}; cancelled=${cancelled.sum()}")
    }

    private companion object { val sequence = AtomicLong() }
}
