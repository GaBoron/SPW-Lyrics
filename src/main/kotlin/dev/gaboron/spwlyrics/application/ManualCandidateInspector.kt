package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.provider.LyricsProvider
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal data class ManualCandidateRequest(
    val provider: LyricsProvider,
    val candidate: LyricsCandidate,
)

/** Resolves manual-search quality from parsed lyrics and reuses the downloaded document for preview/apply. */
internal class ManualCandidateInspector(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxParallelism: Int = DEFAULT_PARALLELISM,
    private val maxCachedDocuments: Int = DEFAULT_CACHE_SIZE,
) {
    private val documents = object : LinkedHashMap<String, LyricsDocument>(maxCachedDocuments, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsDocument>?): Boolean =
            size > maxCachedDocuments
    }

    fun inspect(requests: List<ManualCandidateRequest>): List<LyricsCandidate> {
        if (requests.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(
            minOf(maxParallelism, requests.size),
        ) { runnable ->
            Thread(runnable, "spw-manual-lyrics-${THREAD_NUMBER.incrementAndGet()}").apply { isDaemon = true }
        }
        return try {
            val tasks = requests.map { request ->
                Callable { cached(request.candidate) ?: fetch(request) }
            }
            val futures = executor.invokeAll(tasks, timeoutMillis, TimeUnit.MILLISECONDS)
            requests.zip(futures).map { (request, future) ->
                val document = runCatching { if (future.isCancelled) null else future.get() }.getOrNull()
                request.candidate.copy(qualityHint = document?.quality)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Synchronized
    fun cached(candidate: LyricsCandidate): LyricsDocument? = documents[cacheKey(candidate)]

    private fun fetch(request: ManualCandidateRequest): LyricsDocument? =
        runCatching { request.provider.fetch(request.candidate) }
            .getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
            ?.also { cache(request.candidate, it) }

    @Synchronized
    private fun cache(candidate: LyricsCandidate, document: LyricsDocument) {
        documents[cacheKey(candidate)] = document
    }

    private fun cacheKey(candidate: LyricsCandidate): String = "${candidate.source.name}:${candidate.remoteId}"

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 7_000L
        const val DEFAULT_PARALLELISM = 8
        const val DEFAULT_CACHE_SIZE = 64
        val THREAD_NUMBER = AtomicInteger()
    }
}
