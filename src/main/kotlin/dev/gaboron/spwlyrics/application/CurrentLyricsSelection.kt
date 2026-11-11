package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery

/** A read-only view of the selection slot, not a cache entry or a manual-search preview. */
data class CurrentLyricsSelection(
    val query: TrackQuery,
    val document: LyricsDocument?,
    val delayMs: Int,
    val updating: Boolean,
    val trackGeneration: Long,
    val followingHost: Boolean,
    val embedded: Boolean,
)
