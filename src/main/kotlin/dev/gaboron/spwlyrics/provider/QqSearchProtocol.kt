package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** QQ's anonymous Lite, Mobile, Desktop and compatibility requests share validation and decoding. */
internal class QqSearchProtocol(private val http: ProviderHttp) {
    enum class Variant { LITE, MOBILE, DESKTOP, LEGACY }

    fun search(variant: Variant, keywords: String): List<LyricsCandidate> {
        val query = providerJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(keywords))
        val response = when (variant) {
            Variant.LITE -> {
                val request = """{"comm":{"ct":11,"cv":1003006,"v":1003006,"os_ver":"15","phonetype":"24122RKC7C","tmeAppID":"qqmusiclight","nettype":"NETWORK_WIFI"},"req_1":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicLite","param":{"query":$query,"search_type":0,"num_per_page":30,"page_num":1}}}"""
                http.postJson(QqMusicProvider.SEARCH_URL, request, HEADERS)
            }
            Variant.MOBILE -> {
                val params = """{"query":$query,"highlight":1,"searchid":"","sub_searchid":0,"search_type":0,"nqc_flag":0,"sin":0,"ein":30,"page_num":1,"num_per_page":30,"cat":2,"grp":1,"remoteplace":"","from":"","multi_zhida":1,"sem":0,"related_data":"","ambi_data":""}"""
                val request = """{"comm":{"ct":"11","v":"20080508","tmeLoginType":"0","tmeLoginMethod":"0"},"$MOBILE_KEY":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicMobile","param":$params}}"""
                http.postJson("https://u6.y.qq.com/cgi-bin/musicu.fcg", request, HEADERS + mapOf(
                    "User-Agent" to "QQMusic 20080508(android)", "Accept" to "*/*", "Content-Type" to "application/x-www-form-urlencoded"))
            }
            Variant.DESKTOP -> {
                val params = """{"query":$query,"num_per_page":20,"page_num":1,"search_type":0}"""
                val request = """{"req_1":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":$params},"comm":{"ct":19,"cv":1873,"uin":0}}"""
                http.postJson("https://shu6.y.qq.com/cgi-bin/musicu.fcg", request, HEADERS)
            }
            Variant.LEGACY -> http.get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&w=${ProviderHttpClient.encode(keywords)}&n=20&p=1", HEADERS)
        }
        return decode(response, variant)
    }

    private fun decode(response: String, variant: Variant): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(response) as? JsonObject ?: error("QQ 搜索响应格式无效")
        check(root.long("code") == 0L) { "QQ 搜索接口返回错误" }
        val songs = if (variant == Variant.LEGACY) {
            checkNotNull(root.obj("data")?.obj("song")) { "QQ 搜索响应缺少歌曲数据" }.array("list")
        } else {
            val result = checkNotNull(root.obj(if (variant == Variant.MOBILE) MOBILE_KEY else "req_1")) { "QQ 搜索响应缺少请求结果" }
            check(result.long("code") == 0L) { "QQ 搜索接口返回错误" }
            val data = checkNotNull(result.obj("data")) { "QQ 搜索响应缺少歌曲数据" }
            check(data.long("code") == null || data.long("code") == 0L) { "QQ 搜索接口返回错误" }
            val body = checkNotNull(data.obj("body")) { "QQ 搜索响应缺少歌曲数据" }
            if (variant == Variant.MOBILE) body.array("item_song") else body.obj("song")?.array("list") ?: body.array("item_song")
        }
        checkNotNull(songs) { "QQ 搜索响应缺少候选列表" }
        return songs.mapNotNull { it.asObject() }
            .flatMap { song -> listOf(song) + (song.array("grp") ?: song.array("group")).orEmpty().mapNotNull { it.asObject() } }
            .mapNotNull { song -> runCatching { candidate(song) }.getOrNull() }.distinctBy(LyricsCandidate::remoteId)
            .also { check(songs.isEmpty() || it.isNotEmpty()) { "QQ 搜索候选格式无效" } }
    }

    private fun candidate(song: JsonObject): LyricsCandidate? {
        val title = song.string("title")?.takeIf(String::isNotBlank) ?: song.string("name")?.takeIf(String::isNotBlank)
            ?: song.string("songname")?.takeIf(String::isNotBlank) ?: return null
        if (clean(title).isBlank()) return null
        val musicId = (song.long("id") ?: song.long("songid"))?.takeIf { it > 0 } ?: return null
        return LyricsCandidate(source = LyricsSource.QQ,
            remoteId = (song.string("mid") ?: song.string("songmid"))?.takeIf(String::isNotBlank) ?: musicId.toString(),
            title = clean(title), artists = song.array("singer").orEmpty().mapNotNull { it.asObject()?.string("name") }.map(::clean).filter(String::isNotBlank),
            album = clean((song.obj("album")?.string("name") ?: song.string("albumname")).orEmpty()),
            durationMs = song.long("interval")?.times(1_000), qualityHint = LyricsQuality.KARAOKE_SYNCED,
            context = mapOf("musicId" to musicId.toString(), "albumMid" to (song.obj("album")?.string("mid") ?: song.string("albummid")).orEmpty()),
            metadata = CandidateMetadata.fields("date" to song.string("time_public"),
                "track_number" to CandidateMetadata.number(song.string("index_album")),
                "disc_number" to song.long("index_cd")?.plus(1)?.toString(),
                "cover_url" to (song.obj("album")?.string("mid") ?: song.string("albummid"))
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }?.let { "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg" }))
    }

    private fun clean(value: String): String = value.replace("<em>", "").replace("</em>", "")

    private companion object {
        const val MOBILE_KEY = "music.search.SearchCgiService.DoSearchForQQMusicMobile"
        val HEADERS = mapOf("Referer" to "https://y.qq.com/", "Origin" to "https://y.qq.com")
    }
}
