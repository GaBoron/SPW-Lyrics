package dev.gaboron.spwlyrics.integration

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** Owns an executable lease until actual process exit, even if a decoder stops waiting. */
internal class AnalysisProcess(val process: Process, lease: AutoCloseable) {
    private val stopping = AtomicBoolean()
    val released: CompletableFuture<Void> = process.onExit().thenRunAsync {
        lease.close()
    }

    fun stop() {
        if (!process.isAlive || !stopping.compareAndSet(false, true)) return
        // Neither page cancellation nor plugin stop waits for an unresponsive child.
        Thread.ofVirtual().name("spw-lyrics-process-stop").start {
            runCatching { process.destroyForcibly() }.onFailure {
                System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "终止音频分析进程失败，保留资源租约", it)
            }
        }
    }
}
