package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery

interface LyricsProvider {
    val source: LyricsSource
    fun search(query: TrackQuery, keywords: String, limit: Int = 20): List<LyricsCandidate>
    fun searchManual(query: TrackQuery, keywords: String, limit: Int = 20): List<LyricsCandidate> =
        search(query, keywords, limit)
    fun fetch(candidate: LyricsCandidate): LyricsDocument?
}

class LocalLyricsProvider : LyricsProvider {
    override val source: LyricsSource = LyricsSource.LOCAL
    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> = emptyList()
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
}
