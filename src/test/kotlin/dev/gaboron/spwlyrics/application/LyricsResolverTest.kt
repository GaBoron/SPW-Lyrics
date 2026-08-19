package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
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
    fun `relaxed regional Apple match keeps priority over exact QQ lyrics`() {
        val called = mutableListOf<LyricsSource>()
        val apple = object : LyricsProvider {
            override val source = LyricsSource.APPLE_MUSIC
            override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                called += source
                return listOf(LyricsCandidate(source, "apple", "Song International", listOf("Artist"), "Other Album"))
            }
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = wordDocument(source)
        }
        val qq = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                called += source
                return listOf(LyricsCandidate(source, "qq", "Song", listOf("Artist"), "Album"))
            }
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = wordDocument(source, translation = "翻译")
        }

        val result = LyricsResolver(listOf(qq, apple)).resolveAutomatic(TrackQuery("Song", listOf("Artist"), "Album"))

        assertEquals(LyricsSource.APPLE_MUSIC, result?.candidate?.source)
        assertEquals("翻译", result?.document?.lines?.single()?.translation)
    }

    @Test
    fun `manually selected Apple lyrics receive translation across artist aliases`() {
        val apple = object : LyricsProvider {
            override val source = LyricsSource.APPLE_MUSIC
            override fun search(query: TrackQuery, keywords: String, limit: Int) = emptyList<LyricsCandidate>()
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = wordDocument(source)
        }
        val qq = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, "qq", "unravel", listOf("TK from 凛として時雨"), "unravel", 238_000),
            )
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = wordDocument(source, "翻译")
        }
        val selected = LyricsCandidate(
            LyricsSource.APPLE_MUSIC,
            "apple",
            "Unravel",
            listOf("TK from Ling tosite sigure"),
            "Fantastic Magic",
            238_360,
        )

        val result = LyricsResolver(listOf(apple, qq)).fetchManual(selected)

        assertEquals(LyricsSource.APPLE_MUSIC, result?.candidate?.source)
        assertEquals("翻译", result?.document?.lines?.single()?.translation)
        assertEquals(listOf("QQ音乐"), result?.document?.metadata?.get("translationSource"))
    }

    @Test
    fun `translation enrichment continues after a source without translations`() {
        fun provider(source: LyricsSource, translation: String?) = object : LyricsProvider {
            override val source = source
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, source.name, "Song", listOf("Artist"), "Album", 180_000),
            )
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = wordDocument(source, translation)
        }
        val apple = provider(LyricsSource.APPLE_MUSIC, null)
        val qq = provider(LyricsSource.QQ, null)
        val kugou = provider(LyricsSource.KUGOU, "酷狗翻译")
        val selected = LyricsCandidate(
            LyricsSource.APPLE_MUSIC, "apple", "Song", listOf("Artist"), "Album", 180_000,
        )

        val result = LyricsResolver(listOf(apple, qq, kugou)).fetchManual(selected)

        assertEquals("酷狗翻译", result?.document?.lines?.single()?.translation)
        assertEquals(listOf("酷狗音乐"), result?.document?.metadata?.get("translationSource"))
    }

    @Test
    fun `non-English AM lyrics accept a translated-title candidate after content verification`() {
        val originalTexts = listOf("夜に駆ける", "沈むように", "溶けてゆくように", "二人だけの空")
        val apple = documentProvider(
            LyricsSource.APPLE_MUSIC,
            LyricsDocument(
                LyricsSource.APPLE_MUSIC,
                LyricsFormat.TTML,
                originalTexts.mapIndexed { index, text -> LyricLine(index * 5_000L, text = text) },
            ),
        )
        val qq = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, "qq", "奔向夜晚", listOf("YOASOBI"), "夜に駆ける", 240_500),
            )
            override fun fetch(candidate: LyricsCandidate) = LyricsDocument(
                source,
                LyricsFormat.QRC,
                originalTexts.mapIndexed { index, text ->
                    LyricLine(index * 5_000L + 200, text = text, translation = "中文翻译 $index")
                },
            )
        }
        val selected = LyricsCandidate(
            LyricsSource.APPLE_MUSIC,
            "apple",
            "夜に駆ける",
            listOf("YOASOBI"),
            "THE BOOK",
            240_000,
        )

        val result = LyricsResolver(listOf(apple, qq)).fetchManual(selected)

        assertEquals(originalTexts.indices.map { "中文翻译 $it" }, result?.document?.lines?.map(LyricLine::translation))
        assertEquals(listOf("QQ音乐"), result?.document?.metadata?.get("translationSource"))
    }

    @Test
    fun `same-duration candidate with unrelated lyrics is not used as translation`() {
        val originalTexts = listOf("First lyric", "Second lyric", "Third lyric", "Fourth lyric")
        val apple = documentProvider(
            LyricsSource.APPLE_MUSIC,
            LyricsDocument(
                LyricsSource.APPLE_MUSIC,
                LyricsFormat.TTML,
                originalTexts.mapIndexed { index, text -> LyricLine(index * 5_000L, text = text) },
            ),
        )
        val qq = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, "wrong", "Different title", listOf("Artist"), durationMs = 240_200),
            )
            override fun fetch(candidate: LyricsCandidate) = LyricsDocument(
                source,
                LyricsFormat.QRC,
                List(4) { index -> LyricLine(index * 5_000L, text = "Unrelated $index", translation = "错误 $index") },
            )
        }
        val selected = LyricsCandidate(
            LyricsSource.APPLE_MUSIC,
            "apple",
            "Original title",
            listOf("Artist"),
            durationMs = 240_000,
        )

        val result = LyricsResolver(listOf(apple, qq)).fetchManual(selected)

        assertTrue(result?.document?.lines.orEmpty().all { it.translation == null })
        assertNull(result?.document?.metadata?.get("translationSource"))
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
    fun `word synced provider is preferred when Apple only has line timing`() {
        val called = mutableListOf<LyricsSource>()
        val appleLine = fakeProvider(LyricsSource.APPLE_MUSIC, called, wordSynced = false)
        val neteaseWord = fakeProvider(LyricsSource.NETEASE, called, wordSynced = true)

        val result = LyricsResolver(listOf(appleLine, neteaseWord))
            .resolveAutomatic(TrackQuery("Song", listOf("Artist"), "Album"))

        assertEquals(LyricsSource.NETEASE, result?.candidate?.source)
        assertEquals(listOf(LyricsSource.APPLE_MUSIC, LyricsSource.NETEASE), called.distinct())
    }

    @Test
    fun `manual results use actual quality and put word timing before Apple line timing`() {
        fun provider(source: LyricsSource, document: LyricsDocument) = object : LyricsProvider {
            override val source = source
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, source.name, "Song", listOf("Artist"), qualityHint = LyricsQuality.WORD_SYNCED),
            )
            override fun fetch(candidate: LyricsCandidate): LyricsDocument = document
        }
        val apple = provider(
            LyricsSource.APPLE_MUSIC,
            LyricsDocument(LyricsSource.APPLE_MUSIC, LyricsFormat.TTML, listOf(LyricLine(1_000, 2_000, "line"))),
        )
        val qq = provider(LyricsSource.QQ, wordDocument(LyricsSource.QQ))

        val results = LyricsResolver(listOf(apple, qq)).searchManual(
            TrackQuery("Song", listOf("Artist"), "Album"),
            "Song Artist",
            null,
        )

        assertEquals(LyricsSource.QQ, results.first().candidate.source)
        assertEquals(LyricsQuality.WORD_SYNCED, results.first().candidate.qualityHint)
        assertEquals(LyricsQuality.LINE_SYNCED, results.last().candidate.qualityHint)
    }

    @Test
    fun `manual preview reuses the document downloaded while detecting quality`() {
        var fetches = 0
        val qq = object : LyricsProvider {
            override val source = LyricsSource.QQ
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, "qq", "Song", listOf("Artist"), "Album"),
            )
            override fun fetch(candidate: LyricsCandidate): LyricsDocument =
                wordDocument(source).also { fetches++ }
        }
        val resolver = LyricsResolver(listOf(qq))
        val candidate = resolver.searchManual(
            TrackQuery("Song", listOf("Artist"), "Album"),
            "Song Artist",
            LyricsSource.QQ,
        ).single().candidate

        val resolved = resolver.fetchManual(candidate)

        assertEquals(LyricsQuality.WORD_SYNCED, resolved?.document?.quality)
        assertEquals(1, fetches)
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

        override fun fetch(candidate: LyricsCandidate): LyricsDocument = if (wordSynced) {
            wordDocument(source, translation)
        } else {
            LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "lyrics", translation = translation)))
        }
    }

    private fun emptyProvider(source: LyricsSource, called: MutableList<LyricsSource>) = object : LyricsProvider {
        override val source = source
        override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
            called += source
            return emptyList()
        }

        override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
    }

    private fun documentProvider(source: LyricsSource, document: LyricsDocument) = object : LyricsProvider {
        override val source = source
        override fun search(query: TrackQuery, keywords: String, limit: Int) = emptyList<LyricsCandidate>()
        override fun fetch(candidate: LyricsCandidate) = document
    }

    private fun wordDocument(source: LyricsSource, translation: String? = null) = LyricsDocument(
        source,
        LyricsFormat.TTML,
        listOf(
            LyricLine(
                1_000,
                2_000,
                "lyrics",
                words = listOf(
                    dev.gaboron.spwlyrics.domain.LyricWord(1_000, 1_500, "ly"),
                    dev.gaboron.spwlyrics.domain.LyricWord(1_500, 2_000, "rics"),
                ),
                translation = translation,
            ),
        ),
    )
}
