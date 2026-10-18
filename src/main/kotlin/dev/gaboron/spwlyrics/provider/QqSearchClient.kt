package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Keeps QQ's business status, alternate request and result decoding out of lyric fetching. */
internal class QqSearchClient(private val http: ProviderHttp) {
    fun search(keywords: String, limit: Int): List<LyricsCandidate> {
        if (keywords.isBlank() || limit <= 0) return emptyList()
        val query = providerJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(keywords.trim()))
        val params = """{"query":$query,"num_per_page":20,"page_num":1,"search_type":0}"""
        val call = """{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":$params}"""
        for (alternate in listOf(false, true)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("QQ search cancelled")
            val results = runCatching {
                val response = if (alternate) {
                    http.get("$FALLBACK_URL?format=json&w=${ProviderHttpClient.encode(keywords.trim())}&n=20&p=1", HEADERS)
                } else {
                    val request = """{"req_1":$call,"comm":{"ct":"19","cv":"1859"}}"""
                    http.postJson(QqMusicProvider.SEARCH_URL, request, HEADERS)
                }
                decode(response, alternate)
            }.getOrElse { failure ->
                if (failure is InterruptedException || Thread.currentThread().isInterrupted) throw failure
                emptyList()
            }
            if (results.isNotEmpty()) return results.take(limit)
        }
        return emptyList()
    }

    private fun decode(response: String, alternate: Boolean): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(response) as? JsonObject ?: return emptyList()
        if (root.long("code") != 0L) return emptyList()
        val songs = if (alternate) {
            root.obj("data")?.obj("song")?.array("list")
        } else {
            val result = root.obj("req_1") ?: return emptyList()
            if (result.long("code") != 0L) return emptyList()
            result.obj("data")?.obj("body")?.obj("song")?.array("list")
        }
        return songs.orEmpty()
            .mapNotNull { it.asObject() }
            .flatMap { song ->
                listOf(song) + (song.array("grp") ?: song.array("group")).orEmpty().mapNotNull { it.asObject() }
            }
            .mapNotNull { song -> runCatching { candidate(song) }.getOrNull() }
            .distinctBy(LyricsCandidate::remoteId)
    }

    private fun candidate(song: JsonObject): LyricsCandidate? {
        val title = song.string("title")?.takeIf(String::isNotBlank)
            ?: song.string("name")?.takeIf(String::isNotBlank)
            ?: song.string("songname")?.takeIf(String::isNotBlank) ?: return null
        val musicId = (song.long("id") ?: song.long("songid"))?.takeIf { it > 0 } ?: return null
        return LyricsCandidate(
            source = LyricsSource.QQ,
            remoteId = (song.string("mid") ?: song.string("songmid"))?.takeIf(String::isNotBlank) ?: musicId.toString(),
            title = title,
            artists = song.array("singer").orEmpty().mapNotNull { it.asObject()?.string("name") },
            album = (song.obj("album")?.string("name") ?: song.string("albumname")).orEmpty(),
            durationMs = song.long("interval")?.times(1_000),
            qualityHint = LyricsQuality.KARAOKE_SYNCED,
            context = mapOf("musicId" to musicId.toString()),
        )
    }

    private companion object {
        const val FALLBACK_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        val HEADERS = mapOf("Referer" to "https://y.qq.com/", "Origin" to "https://y.qq.com")
    }
}
