package dev.gaboron.spwlyrics.application

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

/** Shares a song's work with playback and interrupts obsolete searches on cancellation. */
internal class LyricsResolutionTasks(
    private val foreground: ExecutorService,
    private val background: ExecutorService = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "spw-lyrics-precache-search").apply { isDaemon = true }
    },
    private val performance: PluginPerformance = PluginPerformance(),
) : AutoCloseable {
    private data class Work(val task: FutureTask<Unit>, val executor: ExecutorService)
    private val work = ConcurrentHashMap<String, Work>()
    @Volatile private var closed = false

    fun contains(key: String): Boolean = work.containsKey(key)

    @Synchronized
    fun start(key: String, background: Boolean = false, action: (isActive: () -> Boolean) -> Unit): Future<*> {
        val submitted = System.nanoTime()
        lateinit var entry: Work
        val task = object : FutureTask<Unit>({
            performance.record(PluginPerformance.Operation.SEARCH_QUEUE, System.nanoTime() - submitted)
            performance.measure(PluginPerformance.Operation.SEARCH) {
                action { !closed && work[key] === entry && !entry.task.isCancelled }
            }
        }, Unit) {
            override fun done() {
                work.remove(key, entry)
                if (isCancelled) {
                    performance.cancelled()
                    (entry.executor as? ThreadPoolExecutor)?.remove(this)
                }
            }
        }
        entry = Work(task, if (background) this.background else foreground)
        work.putIfAbsent(key, entry)?.let { return it.task }
        if (closed) task.cancel(true)
        else try {
            performance.pending(work.size)
            entry.executor.execute(task)
        } catch (error: RuntimeException) {
            task.cancel(true)
            throw error
        }
        return task
    }

    @Synchronized
    fun cancel(key: String) {
        work.remove(key)?.let {
            it.task.cancel(true)
        }
    }

    @Synchronized
    fun cancelExcept(vararg keys: String) {
        work.keys.filter { it !in keys }.forEach(::cancel)
    }

    fun cancelAll() { work.keys.forEach(::cancel) }

    @Synchronized
    override fun close() {
        closed = true
        cancelAll()
        background.shutdownNow()
        foreground.shutdownNow()
    }
}
