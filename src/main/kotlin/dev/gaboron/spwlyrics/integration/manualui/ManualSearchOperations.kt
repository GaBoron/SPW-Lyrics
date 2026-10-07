package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.application.ResolvedLyrics
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery

/** Keeps the window independent of host APIs, cache storage, and matching rules. */
internal interface ManualSearchOperations {
    fun currentQuery(): TrackQuery?
    fun search(query: TrackQuery, keywords: String, source: LyricsSource?): List<CandidateScore>
    fun preview(candidate: LyricsCandidate): ResolvedLyrics?
    fun apply(query: TrackQuery, candidate: LyricsCandidate): Boolean
    fun useLocal(query: TrackQuery): Boolean
    fun useAutomatic(query: TrackQuery): Boolean
    fun delay(query: TrackQuery): Int
    fun setDelay(query: TrackQuery, delayMs: Int): Boolean
    fun openBatch()
}
