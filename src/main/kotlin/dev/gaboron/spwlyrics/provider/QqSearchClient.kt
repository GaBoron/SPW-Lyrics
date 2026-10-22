package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** A slow endpoint cannot consume the fallback's entire budget. Only nonempty results are cached. */
internal class QqSearchClient(http: ProviderHttp, private val timeoutMillis: Long = 5_000,
    private val hedgeDelayMillis: Long = 900, private val nanoTime: () -> Long = System::nanoTime) : AutoCloseable {
    private val protocol = QqSearchProtocol(http)
    private val requests = Executors.newVirtualThreadPerTaskExecutor()
    private data class Cached(val rows: List<LyricsCandidate>, val created: Long)
    private val cache = object : LinkedHashMap<String, Cached>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 64
    }

    fun search(keywords: String, limit: Int): List<LyricsCandidate> {
        if (keywords.isBlank() || limit <= 0) return emptyList()
        if (Thread.currentThread().isInterrupted) throw InterruptedException("QQ search cancelled")
        val key = keywords.trim()
        synchronized(cache) {
            cache[key]?.takeIf { nanoTime() - it.created < TimeUnit.MINUTES.toNanos(2) }?.let { return it.rows.take(limit) }
        }
        val completion = ExecutorCompletionService<Result<List<LyricsCandidate>>>(requests)
        val pending = mutableListOf<Future<Result<List<LyricsCandidate>>>>()
        val variants = QqSearchProtocol.Variant.entries
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var nextLaunch = nanoTime()
        var finished = 0
        var validResponse = false
        var lastFailure: Throwable? = null

        fun launchNext() {
            val variant = variants[pending.size]
            pending += completion.submit { runCatching { protocol.search(variant, key) } }
            nextLaunch = nanoTime() + TimeUnit.MILLISECONDS.toNanos(hedgeDelayMillis)
        }
        try {
            launchNext()
            while (finished < pending.size || pending.size < variants.size) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("QQ search cancelled")
                val remaining = deadline - nanoTime()
                if (remaining <= 0) break
                val wait = if (pending.size < variants.size) minOf(remaining, (nextLaunch - nanoTime()).coerceAtLeast(0)) else remaining
                val completed = completion.poll(wait, TimeUnit.NANOSECONDS)
                if (completed == null) {
                    if (pending.size < variants.size && nanoTime() >= nextLaunch && nanoTime() < deadline) launchNext()
                    continue
                }
                finished++
                val attempt = completed.get()
                val rows = attempt.getOrElse { lastFailure = it; emptyList() }
                if (attempt.isSuccess) validResponse = true
                if (rows.isNotEmpty()) {
                    synchronized(cache) { cache[key] = Cached(rows.toList(), nanoTime()) }
                    return rows.take(limit)
                }
                if (pending.size < variants.size) launchNext()
            }
            if (validResponse) return emptyList()
            if (finished < pending.size) throw SocketTimeoutException("QQ 搜索在 ${timeoutMillis}ms 内未完成").apply {
                lastFailure?.let(::addSuppressed)
            }
            throw checkNotNull(lastFailure)
        } finally {
            pending.filterNot { it.isDone }.forEach { it.cancel(true) }
        }
    }

    override fun close() {
        requests.shutdownNow()
        synchronized(cache) { cache.clear() }
    }
}
