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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsResolverTest {
    @Test
    fun `keeps higher priority word lyrics and supplements translation`() {
        val called = mutableListOf<LyricsSource>()
        val amll = fakeProvider(LyricsSource.AMLL, called, wordSynced = true)
        val qq = fakeProvider(LyricsSource.QQ, called, translation = "翻译")
        val query = TrackQuery("Song", listOf("Artist"), "Album")

        val result = LyricsResolver(listOf(amll, qq)).resolveAutomatic(query)

        assertEquals(LyricsSource.AMLL, result?.candidate?.source)
        assertEquals(LyricsSource.AMLL, result?.document?.source)
        assertEquals(listOf(LyricsSource.AMLL, LyricsSource.QQ), called.distinct())
        assertEquals("翻译", result?.document?.lines?.single()?.translation)
        assertEquals(2, result?.document?.lines?.single()?.words?.size)
        assertEquals(listOf("QQ音乐"), result?.document?.metadata?.get("translationSource"))
        assertTrue(result?.encoded.orEmpty().contains("歌词来源：AMLL TTML DB；翻译：QQ音乐"))
    }

    @Test
    fun `does not replace an existing primary translation`() {
        val called = mutableListOf<LyricsSource>()
        val amll = fakeProvider(LyricsSource.AMLL, called, wordSynced = true, translation = "原翻译")
        val qq = fakeProvider(LyricsSource.QQ, called, translation = "其他翻译")

        val result = LyricsResolver(listOf(amll, qq))
            .resolveAutomatic(TrackQuery("Song", listOf("Artist"), "Album"))

        assertEquals("原翻译", result?.document?.lines?.single()?.translation)
        assertEquals(listOf(LyricsSource.AMLL), called.distinct())
    }

    @Test
    fun `apple music is ordered after amll and before other providers`() {
        val called = mutableListOf<LyricsSource>()
        val emptyAmll = emptyProvider(LyricsSource.AMLL, called)
        val apple = fakeProvider(LyricsSource.APPLE_MUSIC, called, wordSynced = true, translation = "Apple 翻译")
        val qq = fakeProvider(LyricsSource.QQ, called)

        val result = LyricsResolver(listOf(qq, apple, emptyAmll))
            .resolveAutomatic(TrackQuery("Song", listOf("Artist"), "Album"))

        assertEquals(LyricsSource.APPLE_MUSIC, result?.candidate?.source)
        assertEquals(listOf(LyricsSource.AMLL, LyricsSource.APPLE_MUSIC), called.distinct())
        assertNull(result?.document?.lines?.single()?.romanization)
    }

    @Test
    fun `later word synced source wins over earlier line synced source`() {
        val called = mutableListOf<LyricsSource>()
        val amllLine = fakeProvider(LyricsSource.AMLL, called, wordSynced = false)
        val qqWord = fakeProvider(LyricsSource.QQ, called, wordSynced = true)

        val result = LyricsResolver(listOf(amllLine, qqWord))
            .resolveAutomatic(TrackQuery("Song", listOf("Artist"), "Album"))

        assertEquals(LyricsSource.QQ, result?.candidate?.source)
        assertEquals(listOf(LyricsSource.AMLL, LyricsSource.QQ), called.distinct())
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

    @Test
    fun `manual search uses the provider manual search boundary`() {
        var automaticSearches = 0
        var manualSearches = 0
        val provider = object : LyricsProvider {
            override val source = LyricsSource.APPLE_MUSIC
            override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                automaticSearches++
                return emptyList()
            }
            override fun searchManual(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                manualSearches++
                return listOf(LyricsCandidate(source, "manual", "Other Song", listOf("Other Artist")))
            }
            override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
        }

        val results = LyricsResolver(listOf(provider)).searchManual(
            TrackQuery("Current Song", listOf("Current Artist"), "Current Album"),
            "Other Song",
            LyricsSource.APPLE_MUSIC,
        )

        assertEquals(1, results.size)
        assertEquals(1, manualSearches)
        assertEquals(0, automaticSearches)
    }

    private fun fakeProvider(
        source: LyricsSource,
        called: MutableList<LyricsSource>,
        wordSynced: Boolean = false,
        translation: String? = null,
    ) = object : LyricsProvider {
        override val source = source
        override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
            called += source
            return listOf(LyricsCandidate(source, "id", "Song", listOf("Artist"), "Album"))
        }

        override fun fetch(candidate: LyricsCandidate): LyricsDocument = LyricsDocument(
            source,
            if (wordSynced) LyricsFormat.TTML else LyricsFormat.LRC,
            listOf(
                LyricLine(
                    1_000,
                    2_000,
                    "lyrics",
                    words = if (wordSynced) {
                        listOf(
                            dev.gaboron.spwlyrics.domain.LyricWord(1_000, 1_500, "ly"),
                            dev.gaboron.spwlyrics.domain.LyricWord(1_500, 2_000, "rics"),
                        )
                    } else {
                        emptyList()
                    },
                    translation = translation,
                ),
            ),
        )
    }

    private fun emptyProvider(source: LyricsSource, called: MutableList<LyricsSource>) = object : LyricsProvider {
        override val source = source
        override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
            called += source
            return emptyList()
        }

        override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
    }
}
