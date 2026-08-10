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
    fun `qq lyric envelope accepts attributes and cdata`() {
        val values = extractQqLyricValues(
            """<lyric><content type="file"><![CDATA[A1B2]]></content><contentts mime="file"><![CDATA[C3D4]]></contentts></lyric>""",
        )

        assertEquals("A1B2", values["content"])
        assertEquals("C3D4", values["contentts"])
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
