package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.storage.LyricsDelaySettings

internal data class ManualSearchState(
    val query: TrackQuery? = null,
    val keywords: String = "",
    val source: LyricsSource? = null,
    val candidates: List<CandidateScore> = emptyList(),
    val selected: CandidateScore? = null,
    val document: LyricsDocument? = null,
    val delayText: String = "0",
    val savedDelay: Int = 0,
    val readingTrack: Boolean = true,
    val searching: Boolean = false,
    val previewing: Boolean = false,
    val mutating: Boolean = false,
    val searched: Boolean = false,
    val status: String = "正在读取当前歌曲…",
    val error: Boolean = false,
) {
    val delayValue: Int? get() = delayText.toIntOrNull()?.takeIf {
        it in LyricsDelaySettings.MIN_MS..LyricsDelaySettings.MAX_MS
    }
    val busy: Boolean get() = readingTrack || searching || previewing || mutating
    val canSearch: Boolean get() = query != null && !readingTrack && !searching && !mutating
    val canMutate: Boolean get() = query != null && !busy
}
