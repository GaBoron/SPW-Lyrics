package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsResolverTest {
    @Test
    fun `stops after reliable higher priority provider`() {
        val called = mutableListOf<LyricsSource>()
        val amll = fakeProvider(LyricsSource.AMLL, called)
        val qq = fakeProvider(LyricsSource.QQ, called)
        val cache = FileLyricsCache(createTempDirectory("resolver-cache"))
        val query = TrackQuery("Song", listOf("Artist"), "Album")

        val result = LyricsResolver(listOf(amll, qq), cache).resolveAutomatic(query)

        assertEquals(LyricsSource.AMLL, result?.candidate?.source)
        assertEquals(listOf(LyricsSource.AMLL), called.distinct())
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
