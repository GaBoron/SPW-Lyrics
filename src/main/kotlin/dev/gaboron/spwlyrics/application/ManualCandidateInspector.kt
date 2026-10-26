package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.provider.LyricsProvider
import java.util.LinkedHashMap

/** Downloads only the selected candidate and reuses its parsed document for preview/apply. */
internal class ManualCandidateInspector(
    private val maxCachedDocuments: Int = 64,
) {
    private val documents = object : LinkedHashMap<String, LyricsDocument>(maxCachedDocuments, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsDocument>?): Boolean =
            size > maxCachedDocuments
    }

    @Synchronized
    fun cached(candidate: LyricsCandidate): LyricsDocument? = documents[cacheKey(candidate)]

    fun fetch(provider: LyricsProvider, candidate: LyricsCandidate): LyricsDocument? = cached(candidate) ?:
        runCatching { provider.fetch(candidate) }
            .getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
            ?.also { cache(candidate, it) }

    @Synchronized
    private fun cache(candidate: LyricsCandidate, document: LyricsDocument) {
        documents[cacheKey(candidate)] = document
    }

    private fun cacheKey(candidate: LyricsCandidate): String = "${candidate.source.name}:${candidate.remoteId}"
}
