package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsResolverTest {
    @Test
    fun `stops after reliable higher priority provider`() {
        val called = mutableListOf<LyricsSource>()
        val amll = fakeProvider(LyricsSource.AMLL, called)
        val qq = fakeProvider(LyricsSource.QQ, called)
        val query = TrackQuery("Song", listOf("Artist"), "Album")

        val result = LyricsResolver(listOf(amll, qq)).resolveAutomatic(query)

        assertEquals(LyricsSource.AMLL, result?.candidate?.source)
        assertEquals(listOf(LyricsSource.AMLL), called.distinct())
    }

    @Test
    fun `repeats unresolved searches instead of caching provider candidates`() {
        var searches = 0
        val provider = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                searches++
                return listOf(LyricsCandidate(source, "wrong", "Different Song", listOf("Other Artist"), "Other Album"))
            }
            override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
        }
        val resolver = LyricsResolver(listOf(provider))
        val query = TrackQuery("Missing", listOf("Artist"), "Album")

        assertTrue(resolver.searchManual(query, "Missing Artist", LyricsSource.QQ).isNotEmpty())
        assertTrue(resolver.searchManual(query, "Missing Artist", LyricsSource.QQ).isNotEmpty())

        assertEquals(2, searches)
    }

    private fun fakeProvider(source: LyricsSource, called: MutableList<LyricsSource>) = object : LyricsProvider {
        override val source = source
        override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
            called += source
            return listOf(LyricsCandidate(source, "id", "Song", listOf("Artist"), "Album"))
        }

        override fun fetch(candidate: LyricsCandidate): LyricsDocument =
            LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "lyrics")))
    }
}
