package dev.gaboron.spwlyrics.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchEngineTest {
    private val query = TrackQuery(
        title = "打上花火",
        artists = listOf("DAOKO", "米津玄師"),
        album = "打上花火",
    )

    @Test
    fun `selects a reliable exact match`() {
        val candidate = LyricsCandidate(
            source = LyricsSource.QQ,
            remoteId = "1",
            title = "打上花火",
            artists = listOf("DAOKO", "米津玄師"),
            album = "打上花火",
        )

        val decision = MatchEngine.decide(query, listOf(candidate))

        assertEquals(candidate, decision.winner?.candidate)
        assertTrue(decision.winner!!.score >= MatchEngine.MIN_TOTAL)
    }

    @Test
    fun `rejects close ambiguous candidates`() {
        val candidates = listOf(
            LyricsCandidate(LyricsSource.QQ, "1", "打上花火", listOf("DAOKO", "米津玄師"), "打上花火"),
            LyricsCandidate(LyricsSource.QQ, "2", "打上花火", listOf("DAOKO", "米津玄師"), "打上花火"),
        )

        val decision = MatchEngine.decide(query, candidates)

        assertNull(decision.winner)
        assertTrue(decision.ambiguous)
    }

    @Test
    fun `rejects version conflict`() {
        val candidate = LyricsCandidate(
            source = LyricsSource.NETEASE,
            remoteId = "3",
            title = "打上花火 (Live)",
            artists = listOf("DAOKO", "米津玄師"),
            album = "打上花火",
        )

        assertNull(MatchEngine.decide(query, listOf(candidate)).winner)
    }

    @Test
    fun `matches traditional and simplified metadata`() {
        val simplified = TrackQuery("后来", listOf("刘若英"), "后来")
        val traditional = LyricsCandidate(LyricsSource.AMLL, "4", "後來", listOf("劉若英"), "後來")

        assertEquals(traditional, MatchEngine.decide(simplified, listOf(traditional)).winner?.candidate)
    }

    @Test
    fun `missing artist metadata is manual only`() {
        val incomplete = TrackQuery("打上花火", emptyList(), "打上花火")
        val candidate = LyricsCandidate(LyricsSource.QQ, "5", "打上花火", listOf("DAOKO"), "打上花火")

        assertNull(MatchEngine.decide(incomplete, listOf(candidate)).winner)
    }

    @Test
    fun `splits featured artists without losing the primary artist`() {
        assertEquals(listOf("DAOKO", "米津玄師"), TrackQuery.splitArtists("DAOKO feat. 米津玄師"))
    }

    @Test
    fun `instrumental version is rejected as a conflict`() {
        val candidate = LyricsCandidate(LyricsSource.KUGOU, "6", "打上花火 Instrumental", query.artists, query.album)

        assertNull(MatchEngine.decide(query, listOf(candidate)).winner)
    }
}
