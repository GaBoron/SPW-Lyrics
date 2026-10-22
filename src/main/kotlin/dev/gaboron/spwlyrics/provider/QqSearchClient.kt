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
        var validResponse = false
        var lastFailure: Throwable? = null
        for (alternate in listOf(false, true)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("QQ search cancelled")
            val attempt = runCatching {
                val response = if (alternate) {
                    http.get("$FALLBACK_URL?format=json&w=${ProviderHttpClient.encode(keywords.trim())}&n=20&p=1", HEADERS)
                } else {
                    val request = """{"req_1":$call,"comm":{"ct":"19","cv":"1859"}}"""
                    http.postJson(QqMusicProvider.SEARCH_URL, request, HEADERS)
                }
                decode(response, alternate)
            }
            val results = attempt.getOrElse { failure ->
                if (failure is InterruptedException || Thread.currentThread().isInterrupted) throw failure
                lastFailure = failure
                emptyList()
            }
            if (attempt.isSuccess) validResponse = true
            if (results.isNotEmpty()) return results.take(limit)
        }
        if (validResponse) return emptyList()
        throw checkNotNull(lastFailure)
    }

    private fun decode(response: String, alternate: Boolean): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(response) as? JsonObject ?: error("QQ 搜索响应格式无效")
        check(root.long("code") == 0L) { "QQ 搜索接口返回错误" }
        val songs = if (alternate) {
            checkNotNull(root.obj("data")?.obj("song")) { "QQ 搜索响应缺少歌曲数据" }.array("list")
        } else {
            val result = checkNotNull(root.obj("req_1")) { "QQ 搜索响应缺少请求结果" }
            check(result.long("code") == 0L) { "QQ 搜索接口返回错误" }
            checkNotNull(result.obj("data")?.obj("body")?.obj("song")) { "QQ 搜索响应缺少歌曲数据" }.array("list")
        }
        checkNotNull(songs) { "QQ 搜索响应缺少候选列表" }
        return songs
            .mapNotNull { it.asObject() }
            .flatMap { song ->
                listOf(song) + (song.array("grp") ?: song.array("group")).orEmpty().mapNotNull { it.asObject() }
            }
            .mapNotNull { song -> runCatching { candidate(song) }.getOrNull() }
            .distinctBy(LyricsCandidate::remoteId)
            .also { check(songs.isEmpty() || it.isNotEmpty()) { "QQ 搜索候选格式无效" } }
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
