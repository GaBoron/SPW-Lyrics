package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderContractTest {
    private val query = TrackQuery("Song", listOf("Artist"), "Album")

    @Test
    fun `qq search maps platform payload to internal candidate`() {
        val http = FakeHttp(postJsonBody = """{"req_1":{"data":{"body":{"song":{"list":[{"id":123,"mid":"mid","title":"Song","interval":180,"singer":[{"name":"Artist"}],"album":{"name":"Album"}}]}}}}}""")

        val candidate = QqMusicProvider(http).search(query, "Song Artist").single()

        assertEquals(LyricsSource.QQ, candidate.source)
        assertEquals("123", candidate.context["musicId"])
        assertEquals(180_000, candidate.durationMs)
    }

    @Test
    fun `kugou search maps hash and tolerates empty lyric candidates`() {
        val http = FakeHttp(
            getHandler = { url ->
                if (url.contains("song_search")) """{"data":{"lists":[{"FileHash":"hash","SongName":"Song","SingerName":"Artist","AlbumName":"Album","Duration":180}]}}"""
                else """{"candidates":[]}"""
            },
        )
        val provider = KugouMusicProvider(http)
        val candidate = provider.search(query, "Song Artist").single()

        assertEquals("hash", candidate.remoteId)
        assertEquals(null, provider.fetch(candidate))
    }

    @Test
    fun `provider error is isolated as an empty result`() {
        val http = FakeHttp(getHandler = { throw IllegalStateException("HTTP 429") })

        assertTrue(NeteaseMusicProvider(http).search(query, "Song").isEmpty())
        assertTrue(KugouMusicProvider(http).search(query, "Song").isEmpty())
    }

    @Test
    fun `apple music cache maps word synced ttml results`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                """{"results":[{"id":"record","track_name":"Song","artist_name":"Artist","album_name":"Album","duration":180,"isrc":"TEST123","timing_type":"word","lyricsUrl":"https://lyrics-storage.binimum.org/TEST123.ttml"}]}"""
            },
        )

        val candidate = AppleMusicProvider(http).search(query.copy(durationMs = 180_000), "Song Artist").single()

        assertTrue(requested.single().startsWith("${AppleMusicProvider.SEARCH_URL}?track=Song&artist=Artist&album=Album"))
        assertEquals(LyricsSource.APPLE_MUSIC, candidate.source)
        assertEquals(180_000, candidate.durationMs)
        assertEquals(dev.gaboron.spwlyrics.domain.LyricsQuality.WORD_SYNCED, candidate.qualityHint)
        assertEquals("TEST123", candidate.externalIds["isrc"])
    }

    @Test
    fun `apple music cache retries without optional metadata`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                if (url.contains("album=")) {
                    """{"results":[]}"""
                } else {
                    """{"results":[{"id":"record","track_name":"Song","artist_name":"Artist","lyricsUrl":"https://lyrics-storage.binimum.org/TEST.ttml"}]}"""
                }
            },
        )

        val result = AppleMusicProvider(http).search(query.copy(durationMs = 180_000), "Song Artist")

        assertEquals(1, result.size)
        assertEquals(2, requested.size)
        assertTrue(requested.last().contains("track=Song&artist=Artist"))
        assertTrue(!requested.last().contains("album="))
    }

    @Test
    fun `apple automatic search falls back through regional catalog metadata`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                when {
                    url.startsWith(AppleCatalogSearch.SEARCH_URL) ->
                        """{"resultCount":1,"results":[{"trackName":"Regional Title","artistName":"Artist","collectionName":"Regional Album","trackTimeMillis":180000}]}"""
                    url.contains("track=Regional+Title&artist=Artist") ->
                        """{"results":[{"id":"regional","track_name":"Regional Title","artist_name":"Artist","album_name":"Regional Album","duration":180,"lyricsUrl":"https://lyrics-storage.binimum.org/REGIONAL.ttml"}]}"""
                    else -> """{"results":[]}"""
                }
            },
        )

        val result = AppleMusicProvider(http).search(
            TrackQuery("Localized Title", listOf("Artist"), "Localized Album", durationMs = 180_000),
            "Localized Title Artist",
        )

        assertEquals("Regional Title", result.single().title)
        assertTrue(requested.any { it.startsWith(AppleCatalogSearch.SEARCH_URL) })
        assertTrue(requested.any { it.contains("track=Regional+Title&artist=Artist") })
    }

    @Test
    fun `apple music cache honors manual title and artist keywords`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                when {
                    url.startsWith(AppleCatalogSearch.SEARCH_URL) ->
                        """{"resultCount":1,"results":[{"trackName":"golden hour","artistName":"JVKE","collectionName":"Album","trackTimeMillis":209000}]}"""
                    url.contains("track=golden+hour&artist=JVKE") ->
                        """{"results":[{"id":"record","track_name":"golden hour","artist_name":"JVKE","lyricsUrl":"https://lyrics-storage.binimum.org/TEST.ttml"}]}"""
                    else -> """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">Line</p></div></body></tt>"""
                }
            },
        )

        val result = AppleMusicProvider(http).searchManual(
            TrackQuery("Current Song", listOf("Current Artist"), "Current Album"),
            "golden hour",
        )

        assertEquals("golden hour", result.single().title)
        assertTrue(requested.any { it.startsWith(AppleCatalogSearch.SEARCH_URL) && it.contains("term=golden+hour") })
        assertTrue(requested.any { it.contains("track=golden+hour&artist=JVKE") })
    }

    @Test
    fun `apple manual search reports actual line timing instead of cache hint`() {
        val http = FakeHttp(
            getHandler = { url ->
                when {
                    url.startsWith(AppleCatalogSearch.SEARCH_URL) ->
                        """{"resultCount":1,"results":[{"trackName":"Line Song","artistName":"Artist","collectionName":"Album","trackTimeMillis":180000}]}"""
                    url.startsWith(AppleMusicProvider.SEARCH_URL) ->
                        """{"results":[{"id":"line-record","track_name":"Line Song","artist_name":"Artist","timing_type":"word","lyricsUrl":"https://lyrics-storage.binimum.org/LINE.ttml"}]}"""
                    else ->
                        """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">Line synced</p></div></body></tt>"""
                }
            },
        )

        val candidate = AppleMusicProvider(http).searchManual(query, "unrelated free text").single()

        assertEquals(dev.gaboron.spwlyrics.domain.LyricsQuality.LINE_SYNCED, candidate.qualityHint)
    }

    @Test
    fun `apple music cache fetches and parses word timed ttml`() {
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.APPLE_MUSIC,
            "record",
            "Song",
            listOf("Artist"),
            context = mapOf("url" to "https://lyrics-storage.binimum.org/TEST123.ttml"),
        )
        val http = FakeHttp(
            getHandler = {
                """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s"><span begin="1s" end="1.5s">Hel</span><span begin="1.5s" end="2s">lo</span></p></div></body></tt>"""
            },
        )

        val document = AppleMusicProvider(http).fetch(candidate)

        assertEquals(LyricsSource.APPLE_MUSIC, document?.source)
        assertEquals("Hello", document?.lines?.single()?.text)
        assertEquals(2, document?.lines?.single()?.words?.size)
    }

    @Test
    fun `apple music cache rejects untrusted lyric download urls`() {
        val http = FakeHttp(
            getHandler = {
                """{"results":[{"id":"record","track_name":"Song","artist_name":"Artist","lyricsUrl":"https://example.com/lyrics.ttml"}]}"""
            },
        )

        assertTrue(AppleMusicProvider(http).search(query, "Song").isEmpty())
    }

    @Test
    fun `netease cloud search maps the current response shape`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                """{"code":200,"result":{"songs":[{"id":42,"name":"Song","ar":[{"name":"Artist"}],"al":{"name":"Album"},"dt":180000}]}}"""
            },
        )

        val candidate = NeteaseMusicProvider(http).search(query, "Song Artist", limit = 5).single()

        assertTrue(requested.single().startsWith(NeteaseMusicProvider.CLOUD_SEARCH_URL))
        assertEquals("42", candidate.remoteId)
        assertEquals(listOf("Artist"), candidate.artists)
        assertEquals("Album", candidate.album)
        assertEquals(180_000, candidate.durationMs)
    }

    @Test
    fun `netease search falls back when the cloud endpoint has no songs`() {
        val requested = mutableListOf<String>()
        val http = FakeHttp(
            getHandler = { url ->
                requested += url
                if (url.startsWith(NeteaseMusicProvider.CLOUD_SEARCH_URL)) {
                    """{"code":405,"message":"busy"}"""
                } else {
                    """{"code":200,"result":{"songs":[{"id":42,"name":"Song","artists":[{"name":"Artist"}],"album":{"name":"Album"},"duration":180000}]}}"""
                }
            },
        )

        val candidate = NeteaseMusicProvider(http).search(query, "Song Artist").single()

        assertEquals(
            listOf(NeteaseMusicProvider.CLOUD_SEARCH_URL, NeteaseMusicProvider.LEGACY_SEARCH_URL),
            requested.map { it.substringBefore('?') },
        )
        assertEquals("42", candidate.remoteId)
    }

    @Test
    fun `qq lyric envelope accepts attributes and cdata`() {
        val values = extractQqLyricValues(
            """<lyric><content type="file"><![CDATA[A1B2]]></content><contentts mime="file"><![CDATA[C3D4]]></contentts></lyric>""",
        )

        assertEquals("A1B2", values["content"])
        assertEquals("C3D4", values["contentts"])
    }

    @Test
    fun `qq accepts plain lrc translation beside qrc lyrics`() {
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.QQ,
            "mid",
            "Song",
            listOf("Artist"),
            "Album",
            context = mapOf("musicId" to "123"),
        )
        val http = FakeHttp(
            getHandler = {
                """<lyric><content><![CDATA[[1000,1000]Hello(1000,1000)]]></content><contentts><![CDATA[[00:01.00]你好]]></contentts></lyric>"""
            },
        )

        val line = QqMusicProvider(http).fetch(candidate)?.lines?.single()

        assertEquals("Hello", line?.text)
        assertEquals("你好", line?.translation)
    }

    @Test
    fun `netease falls back to line lyrics when eapi fails`() {
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.NETEASE, "42", "Song", listOf("Artist"), "Album",
        )
        val http = FakeHttp(
            postFormHandler = { _, _ -> throw IllegalStateException("changed endpoint") },
            getHandler = { """{"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":"[00:01.00]你好"}}""" },
        )

        val document = NeteaseMusicProvider(http).fetch(candidate)

        assertEquals("Hello", document?.lines?.single()?.text)
        assertEquals("你好", document?.lines?.single()?.translation)
    }

    @Test
    fun `netease prefers newer translation and romanization tracks`() {
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.NETEASE, "42", "Song", listOf("Artist"), "Album",
        )
        val http = FakeHttp(
            postFormHandler = { _, _ ->
                """{"yrc":{"lyric":"[1000,1000](1000,1000,0)Hello"},"ytlrc":{"lyric":"[00:01.10]你好"},"tlyric":{"lyric":"[00:01.10]旧翻译"},"yromalrc":{"lyric":"[00:01.10]ni hao"}}"""
            },
        )

        val line = NeteaseMusicProvider(http).fetch(candidate)?.lines?.single()

        assertEquals("你好", line?.translation)
        assertEquals("ni hao", line?.romanization)
    }

    @Test
    fun `netease falls back from an empty preferred translation track`() {
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.NETEASE, "42", "Song", listOf("Artist"), "Album",
        )
        val http = FakeHttp(
            postFormHandler = { _, _ ->
                """{"lrc":{"lyric":"[00:01.00]Hello"},"ytlrc":{"lyric":""},"tlyric":{"lyric":"[00:01.00]你好"}}"""
            },
        )

        assertEquals("你好", NeteaseMusicProvider(http).fetch(candidate)?.lines?.single()?.translation)
    }
}

private class FakeHttp(
    private val getHandler: (String) -> String = { "{}" },
    private val postJsonBody: String = "{}",
    private val postFormHandler: (String, Map<String, String>) -> String = { _, _ -> "{}" },
) : ProviderHttp {
    override fun get(url: String, headers: Map<String, String>): String = getHandler(url)
    override fun postJson(url: String, body: String, headers: Map<String, String>): String = postJsonBody
    override fun postForm(url: String, values: Map<String, String>, headers: Map<String, String>): String =
        postFormHandler(url, values)
}
