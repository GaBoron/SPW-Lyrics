package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider

/** Races independent translation lookups so a slow source or query variant cannot block a usable result. */
internal class TranslationSourceResolver(
    providers: Map<LyricsSource, LyricsProvider>,
    private val providerTasks: ProviderTaskPool,
) {
    private val providers = TRANSLATION_SOURCES.mapNotNull(providers::get)

    fun find(
        primary: LyricsDocument,
        primaryCandidate: LyricsCandidate,
        query: TrackQuery,
        deadlineNanos: Long,
    ): TranslationSourceMatch? {
        val attempts = orderedLookups(query, primaryCandidate)
            .flatMap { lookup -> providers.map { provider -> TranslationAttempt(provider, lookup) } }
        return providerTasks.collect(
            tasks = attempts.map { attempt ->
                { findFromAttempt(attempt, primary, deadlineNanos) }
            },
            deadlineNanos = deadlineNanos,
            stopWhen = { completed, _ -> completed.isNotEmpty() },
        ).firstOrNull()?.value
    }

    /** Lets every translation provider finish, then honors provider priority for the final result. */
    fun findAll(
        primary: LyricsDocument,
        primaryCandidate: LyricsCandidate,
        query: TrackQuery,
    ): TranslationSourceMatch? {
        val lookups = orderedLookups(query, primaryCandidate)
        return providerTasks.collect(
            tasks = providers.map { provider ->
                {
                    lookups.firstNotNullOfOrNull { lookup ->
                        findFromAttempt(TranslationAttempt(provider, lookup), primary, Long.MAX_VALUE)
                    }
                }
            },
            deadlineNanos = Long.MAX_VALUE,
        ).map(IndexedValue<TranslationSourceMatch>::value)
            .minByOrNull { it.document.source.priority }
    }

    private fun orderedLookups(query: TrackQuery, primaryCandidate: LyricsCandidate): List<TranslationLookup> {
        val lookupsByQuery = TranslationLookupPlan.queries(query, primaryCandidate).map { lookupQuery ->
            prioritizedKeywords(lookupQuery).map { keywords -> TranslationLookup(lookupQuery, keywords) }
        }
        return (0 until (lookupsByQuery.maxOfOrNull(List<TranslationLookup>::size) ?: 0))
            .flatMap { index -> lookupsByQuery.mapNotNull { it.getOrNull(index) } }
            .distinctBy(TranslationLookup::key)
    }

    private fun findFromAttempt(
        attempt: TranslationAttempt,
        primary: LyricsDocument,
        deadlineNanos: Long,
    ): TranslationSourceMatch? {
        if (System.nanoTime() >= deadlineNanos) return null
        val candidates = runCatching {
            attempt.provider.search(attempt.lookup.query, attempt.lookup.keywords, SEARCH_RESULT_LIMIT)
        }.getOrDefault(emptyList())
        if (candidates.isEmpty() || System.nanoTime() >= deadlineNanos) return null

        val decision = MatchEngine.decide(
            attempt.lookup.query,
            candidates,
            TranslationMatchPolicy::accepts,
        )
        val candidatesToFetch = buildList {
            decision.winner?.let { add(TranslationCandidate(it, requireRecordingEvidence = false)) }
            decision.ranked.asSequence()
                .filter(TranslationMatchPolicy::canVerifyByLyrics)
                .filter { score -> none { it.score.candidate.remoteId == score.candidate.remoteId } }
                .take(MAX_CANDIDATES_PER_ATTEMPT)
                .mapTo(this) { TranslationCandidate(it, requireRecordingEvidence = true) }
        }
        for (candidate in candidatesToFetch) {
            if (System.nanoTime() >= deadlineNanos) return null
            val secondary = runCatching { attempt.provider.fetch(candidate.score.candidate) }.getOrNull()
                ?.takeIf { it.lines.isNotEmpty() && it.lines.any { line -> !line.translation.isNullOrBlank() } }
                ?: continue
            val alignment = CrossSourceLyricsAligner.align(primary, secondary)
            if (candidate.requireRecordingEvidence && !alignment.provesSameRecording) continue
            val enriched = SecondaryLyricsEnricher.enrich(primary, secondary, alignment)
            if (!addsTranslation(primary, enriched)) continue
            return TranslationSourceMatch(secondary, alignment)
        }
        return null
    }

    private fun prioritizedKeywords(query: TrackQuery): List<String> {
        val variants = query.searchQueries()
        return listOfNotNull(
            variants.getOrNull(1),
            variants.getOrNull(2),
            variants.firstOrNull(),
            variants.lastOrNull(),
        ).map(String::trim).filter(String::isNotBlank).distinct().take(MAX_QUERY_VARIANTS)
    }

    private fun addsTranslation(primary: LyricsDocument, enriched: LyricsDocument): Boolean =
        enriched.lines.indices.any { index ->
            primary.lines.getOrNull(index)?.translation.isNullOrBlank() &&
                !enriched.lines[index].translation.isNullOrBlank()
        }

    private data class TranslationLookup(val query: TrackQuery, val keywords: String) {
        val key: String = listOf(
            query.title,
            query.artists.joinToString("/"),
            query.album,
            query.durationMs?.toString().orEmpty(),
            keywords,
        ).joinToString("|")
    }

    private data class TranslationAttempt(
        val provider: LyricsProvider,
        val lookup: TranslationLookup,
    )

    private data class TranslationCandidate(
        val score: CandidateScore,
        val requireRecordingEvidence: Boolean,
    )

    private companion object {
        val TRANSLATION_SOURCES = listOf(LyricsSource.QQ, LyricsSource.KUGOU, LyricsSource.NETEASE)
        const val SEARCH_RESULT_LIMIT = 8
        const val MAX_QUERY_VARIANTS = 4
        const val MAX_CANDIDATES_PER_ATTEMPT = 1
    }
}

internal data class TranslationSourceMatch(
    val document: LyricsDocument,
    val alignment: CrossSourceAlignment,
)
