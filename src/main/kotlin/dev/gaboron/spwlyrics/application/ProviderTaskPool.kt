package dev.gaboron.spwlyrics.application

import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Runs independent provider work concurrently under one caller-owned deadline. */
internal class ProviderTaskPool(
    parallelism: Int = DEFAULT_PARALLELISM,
) : AutoCloseable {
    private val executor = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "spw-provider-${THREAD_NUMBER.incrementAndGet()}").apply { isDaemon = true }
    }

    fun <T> collect(
        tasks: List<() -> T?>,
        deadlineNanos: Long,
        stopWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        onProgress: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Unit = { _, _ -> },
    ): List<IndexedValue<T>> {
        if (tasks.isEmpty()) return emptyList()
        val completion = ExecutorCompletionService<TaskOutcome<T>>(executor)
        val futures = tasks.mapIndexed { index, task ->
            completion.submit {
                TaskOutcome(index, runCatching { task() }.getOrNull())
            }
        }
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

    /** Publishes a deadline snapshot without cancelling unfinished work, then waits for every task. */
    fun <T> collectProgressively(
        tasks: List<() -> T?>,
        snapshotDeadlineNanos: Long,
        snapshotWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        stopWhen: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Boolean = { _, _ -> false },
        onProgress: (completed: List<IndexedValue<T>>, pendingIndices: Set<Int>) -> Unit = { _, _ -> },
        onSnapshot: (List<IndexedValue<T>>) -> Unit,
    ): List<IndexedValue<T>> {
        if (tasks.isEmpty()) {
            onSnapshot(emptyList())
            return emptyList()
        }
        val completion = ExecutorCompletionService<TaskOutcome<T>>(executor)
        val futures = tasks.mapIndexed { index, task ->
            completion.submit {
                TaskOutcome(index, runCatching { task() }.getOrNull())
            }
        }
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
                if (!snapshotPublished && snapshotWhen(completed, pending)) {
                    onSnapshot(completed.sortedBy(IndexedValue<T>::index))
                    snapshotPublished = true
                }
                if (stopWhen(completed, pending)) break
            }
            if (!snapshotPublished) onSnapshot(completed.sortedBy(IndexedValue<T>::index))
            while (pending.isNotEmpty() && !stopWhen(completed, pending)) {
                val outcome = completion.take().get()
                pending -= outcome.index
                outcome.value?.let { completed += IndexedValue(outcome.index, it) }
                onProgress(completed.sortedBy(IndexedValue<T>::index), pending.toSet())
            }
        } finally {
            futures.filterNot { it.isDone }.forEach { it.cancel(true) }
        }
        return completed.sortedBy(IndexedValue<T>::index)
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun remainingNanos(deadlineNanos: Long): Long =
        if (deadlineNanos == Long.MAX_VALUE) Long.MAX_VALUE else deadlineNanos - System.nanoTime()

    private data class TaskOutcome<T>(val index: Int, val value: T?)

    private companion object {
        const val DEFAULT_PARALLELISM = 10
        val THREAD_NUMBER = AtomicInteger()
    }
}
