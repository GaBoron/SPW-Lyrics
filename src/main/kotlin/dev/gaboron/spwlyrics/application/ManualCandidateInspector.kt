package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.provider.LyricsProvider
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class ManualCandidateRequest(val provider: LyricsProvider, val candidate: LyricsCandidate)

/** Identifies candidate types after the list is shown; preview/apply share cached and in-flight reads. */
internal class ManualCandidateInspector(
    private val maxCachedDocuments: Int = 64,
) {
    private val pending = ConcurrentHashMap<String, CompletableFuture<LyricsDocument?>>()
    private val documents = object : LinkedHashMap<String, LyricsDocument>(maxCachedDocuments, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsDocument>?): Boolean =
            size > maxCachedDocuments
    }

    @Synchronized
    fun cached(candidate: LyricsCandidate): LyricsDocument? = documents[cacheKey(candidate)]

    fun inspect(requests: List<ManualCandidateRequest>, report: (LyricsCandidate) -> Unit) {
        if (requests.isEmpty()) return
        val executor = Executors.newFixedThreadPool(minOf(8, requests.size)) {
            Thread(it, "spw-manual-lyrics-type").apply { isDaemon = true }
        }
        try {
            executor.invokeAll(requests.map { request -> Callable {
                val document = fetch(request.provider, request.candidate)
                if (!Thread.currentThread().isInterrupted && document != null) {
                    report(request.candidate.copy(qualityHint = document.quality))
                }
            } }, 20, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    fun fetch(provider: LyricsProvider, candidate: LyricsCandidate): LyricsDocument? {
        cached(candidate)?.let { return it }
        val key = cacheKey(candidate)
        val own = CompletableFuture<LyricsDocument?>()
        val existing = pending.putIfAbsent(key, own)
        if (existing != null) return runCatching { existing.get() }.getOrNull()
        return try {
            val document = cached(candidate) ?: runCatching { provider.fetch(candidate) }.getOrNull()
                ?.takeIf { it.lines.isNotEmpty() }?.also { cache(candidate, it) }
            own.complete(document)
            document
        } catch (error: Throwable) {
            own.completeExceptionally(error)
            throw error
        } finally {
            pending.remove(key, own)
        }
    }

    @Synchronized
    fun cache(candidate: LyricsCandidate, document: LyricsDocument) {
        documents[cacheKey(candidate)] = document
    }

    private fun cacheKey(candidate: LyricsCandidate): String = "${candidate.source.name}:${candidate.remoteId}"
}
