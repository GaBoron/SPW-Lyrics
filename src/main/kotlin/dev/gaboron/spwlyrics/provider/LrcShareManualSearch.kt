package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.ProviderSearchPhase
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject

internal data class LrcShareSearchResult(val data: JsonObject?)

/** A short UI search deadline must not cancel catalog acquisition or the shared online search. */
internal class LrcShareManualSearch(
    private val search: (String, Int) -> JsonObject?,
    private val nanoTime: () -> Long = System::nanoTime,
) : AutoCloseable {
    private data class Key(val keywords: String, val limit: Int)
    private class Entry(val task: FutureTask<LrcShareSearchResult>) {
        var expires = Long.MAX_VALUE
        val listeners = mutableListOf<(ProviderSearchStatus) -> Unit>()
    }
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "spw-lrcshare-manual-search").apply { isDaemon = true }
    }
    private val entries = LinkedHashMap<Key, Entry>(8, .75f, true)
    @Volatile private var closed = false

    fun search(keywords: String, limit: Int, onStatus: (ProviderSearchStatus) -> Unit): LrcShareSearchResult? {
        val key = Key(keywords.trim(), limit)
        val entry = synchronized(this) {
            if (closed) return null
            entries.entries.removeIf { it.value.task.isDone && it.value.expires <= nanoTime() }
            val entry = entries[key] ?: start(key)
            if (!entry.task.isDone) {
                // Register before reporting pending so a fast completion cannot lose its notification.
                entry.listeners += onStatus
                onStatus(ProviderSearchStatus(LyricsSource.LRCSHARE, ProviderSearchPhase.SEARCHING,
                    "正在后台获取目录或搜索，完成后会自动更新结果。"))
                return null
            }
            entry
        }
        return try {
            entry.task.get()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private fun start(key: Key): Entry {
        // Drop superseded queued keywords; leave the running request available to fill its response cache.
        entries.entries.toList().forEach { (oldKey, old) ->
            if (!old.task.isDone && worker.remove(old.task)) {
                entries.remove(oldKey)
                old.task.cancel(false)
            }
        }
        while (entries.size >= 8) {
            val oldest = entries.entries.firstOrNull { it.value.task.isDone } ?: break
            entries.remove(oldest.key)
        }
        lateinit var entry: Entry
        val task = object : FutureTask<LrcShareSearchResult>({ LrcShareSearchResult(search(key.keywords, key.limit)) }) {
            override fun done() {
                val outcome = runCatching { get() }
                val listeners = synchronized(this@LrcShareManualSearch) {
                    if (closed || isCancelled) return
                    if (outcome.isSuccess) entry.expires = nanoTime() + TimeUnit.MINUTES.toNanos(10)
                    else entries.remove(key, entry)
                    entry.listeners.toList().also { entry.listeners.clear() }
                }
                val status = outcome.exceptionOrNull()?.let { error ->
                    ProviderSearchFailure.status(LyricsSource.LRCSHARE, error)
                } ?: ProviderSearchStatus(LyricsSource.LRCSHARE, ProviderSearchPhase.AVAILABLE,
                    "搜索完成，正在更新结果。", resultsChanged = true)
                if (!closed) listeners.forEach { listener -> runCatching { listener(status) } }
            }
        }
        entry = Entry(task)
        entries[key] = entry
        worker.execute(task)
        return entry
    }

    @Synchronized
    override fun close() {
        closed = true
        entries.values.forEach {
            it.listeners.clear()
            it.task.cancel(true)
        }
        entries.clear()
        worker.shutdownNow()
    }
}
