package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricWord
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualCandidateInspectorTest {
    @Test
    fun `uses parsed line timing instead of provider word hint`() {
        val candidate = candidate(LyricsQuality.WORD_SYNCED)
        val provider = provider(LyricsDocument(
            LyricsSource.NETEASE,
            LyricsFormat.LRC,
            listOf(LyricLine(1_000, 2_000, "line")),
        ))

        val inspected = ManualCandidateInspector().inspect(listOf(ManualCandidateRequest(provider, candidate))).single()

        assertEquals(LyricsQuality.LINE_SYNCED, inspected.qualityHint)
    }

    @Test
    fun `uses parsed word timing when provider has no reliable hint`() {
        val candidate = candidate(null)
        val provider = provider(LyricsDocument(
            LyricsSource.NETEASE,
            LyricsFormat.YRC,
            listOf(LyricLine(
                1_000,
                2_000,
                "word",
                words = listOf(LyricWord(1_000, 2_000, "word")),
            )),
        ))

        val inspected = ManualCandidateInspector().inspect(listOf(ManualCandidateRequest(provider, candidate))).single()

        assertEquals(LyricsQuality.WORD_SYNCED, inspected.qualityHint)
    }

    @Test
    fun `marks unavailable lyrics unknown instead of keeping an optimistic hint`() {
        val inspected = ManualCandidateInspector().inspect(
            listOf(ManualCandidateRequest(provider(null), candidate(LyricsQuality.WORD_SYNCED))),
        ).single()

        assertNull(inspected.qualityHint)
    }

    @Test
    fun `caches inspected document for manual preview`() {
        var fetches = 0
        val document = LyricsDocument(
            LyricsSource.NETEASE,
            LyricsFormat.LRC,
            listOf(LyricLine(1_000, 2_000, "line")),
        )
        val provider = object : LyricsProvider {
            override val source = LyricsSource.NETEASE
            override fun search(query: TrackQuery, keywords: String, limit: Int) = emptyList<LyricsCandidate>()
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = document.also { fetches++ }
        }
        val candidate = candidate(LyricsQuality.WORD_SYNCED)
        val inspector = ManualCandidateInspector()

        inspector.inspect(listOf(ManualCandidateRequest(provider, candidate)))

        assertEquals(document, inspector.cached(candidate))
        assertEquals(1, fetches)
    }

    private fun candidate(quality: LyricsQuality?) = LyricsCandidate(
        LyricsSource.NETEASE,
        "42",
        "Song",
        listOf("Artist"),
        qualityHint = quality,
    )

    private fun provider(document: LyricsDocument?) = object : LyricsProvider {
        override val source = LyricsSource.NETEASE
        override fun search(query: TrackQuery, keywords: String, limit: Int) = emptyList<LyricsCandidate>()
        override fun fetch(candidate: LyricsCandidate): LyricsDocument? = document
    }
}
