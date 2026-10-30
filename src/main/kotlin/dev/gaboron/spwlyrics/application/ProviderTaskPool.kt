package dev.gaboron.spwlyrics.application

import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Runs independent provider work concurrently under one caller-owned deadline. */
internal class ProviderTaskPool(
    parallelism: Int = DEFAULT_PARALLELISM,
) : AutoCloseable {
    private val executor = ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "spw-provider-${THREAD_NUMBER.incrementAndGet()}").apply { isDaemon = true }
    }

    fun <T> collect(
        tasks: List<() -> T?>,
        deadlineNanos: Long,
        stopWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        onProgress: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Unit = { _, _ -> },
    ): List<IndexedValue<T>> {
        if (tasks.isEmpty()) return emptyList()
        val completion = LinkedBlockingQueue<FutureTask<TaskOutcome<T>>>()
        val futures = submit(tasks, completion)
        val completed = mutableListOf<IndexedValue<T>>()
        val pending = tasks.indices.toMutableSet()
        try {
            while (pending.isNotEmpty()) {
                val remaining = remainingNanos(deadlineNanos)
                if (remaining <= 0L) break
                val future = if (deadlineNanos == Long.MAX_VALUE) {
                    completion.take()
                } else {
                    completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                }
                val outcome = runCatching { future.get() }.getOrNull() ?: continue
                pending -= outcome.index
                outcome.value?.let { completed += IndexedValue(outcome.index, it) }
                onProgress(completed.sortedBy(IndexedValue<T>::index), pending.toSet())
                if (stopWhen(completed, pending)) break
            }
        } finally {
            futures.filterNot { it.isDone }.forEach { it.cancel(true) }
        }
        return completed.sortedBy(IndexedValue<T>::index)
    }

    /** Publishes usable results progressively while unfinished providers retain their deadline. */
    fun <T> collectProgressively(
        tasks: List<() -> T?>,
        snapshotDeadlineNanos: Long,
        completionDeadlineNanos: Long = Long.MAX_VALUE,
        snapshotWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        stopWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        onProgress: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Unit = { _, _ -> },
        onSnapshot: (List<IndexedValue<T>>) -> Unit,
    ): List<IndexedValue<T>> {
        if (tasks.isEmpty()) {
            onSnapshot(emptyList())
            return emptyList()
        }
        val completion = LinkedBlockingQueue<FutureTask<TaskOutcome<T>>>()
        val futures = submit(tasks, completion)
        val completed = mutableListOf<IndexedValue<T>>()
        val pending = tasks.indices.toMutableSet()
        var snapshotPublished = false
        try {
            while (pending.isNotEmpty()) {
                val remaining = remainingNanos(snapshotDeadlineNanos)
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                val outcome = future.get()
                pending -= outcome.index
                outcome.value?.let { completed += IndexedValue(outcome.index, it) }
                onProgress(completed.sortedBy(IndexedValue<T>::index), pending.toSet())
                if (outcome.value != null && snapshotWhen(completed, pending)) {
                    onSnapshot(completed.sortedBy(IndexedValue<T>::index))
                    snapshotPublished = true
                }
                if (stopWhen(completed, pending)) break
            }
            if (!snapshotPublished) onSnapshot(completed.sortedBy(IndexedValue<T>::index))
            while (pending.isNotEmpty() && !stopWhen(completed, pending)) {
                val remaining = remainingNanos(completionDeadlineNanos)
                if (remaining <= 0L) break
                val future = if (completionDeadlineNanos == Long.MAX_VALUE) {
                    completion.take()
                } else {
                    completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                }
                val outcome = future.get()
                pending -= outcome.index
                outcome.value?.let { completed += IndexedValue(outcome.index, it) }
                onProgress(completed.sortedBy(IndexedValue<T>::index), pending.toSet())
                if (outcome.value != null) onSnapshot(completed.sortedBy(IndexedValue<T>::index))
            }
        } finally {
            futures.filterNot { it.isDone }.forEach { it.cancel(true) }
        }
        return completed.sortedBy(IndexedValue<T>::index)
    }

    override fun close() {
        executor.shutdownNow()
    }

    /** Queue the cancellable task itself, so obsolete providers leave the executor immediately. */
    private fun <T> submit(
        tasks: List<() -> T?>,
        completion: LinkedBlockingQueue<FutureTask<TaskOutcome<T>>>,
    ): List<FutureTask<TaskOutcome<T>>> {
        val submitted = mutableListOf<FutureTask<TaskOutcome<T>>>()
        try {
            tasks.forEachIndexed { index, action ->
                val task = object : FutureTask<TaskOutcome<T>>({ TaskOutcome(index, runCatching(action).getOrNull()) }) {
                    override fun done() {
                        if (isCancelled) executor.remove(this)
                        completion.add(this)
                    }
                }
                submitted += task
                executor.execute(task)
            }
            return submitted
        } catch (error: RuntimeException) {
            submitted.forEach { it.cancel(true) }
            throw error
        }
    }

    private fun remainingNanos(deadlineNanos: Long): Long =
        if (deadlineNanos == Long.MAX_VALUE) Long.MAX_VALUE else deadlineNanos - System.nanoTime()

    private data class TaskOutcome<T>(val index: Int, val value: T?)

    private companion object {
        const val DEFAULT_PARALLELISM = 15
        val THREAD_NUMBER = AtomicInteger()
    }
}
