package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LrcShareLyricsCodec
import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.JsonObject

/** Uses the user's own API key and a catalog-issued session; no other application's key is bundled. */
class LrcShareProvider(private val http: ProviderHttp, private val apiKey: () -> String) : LyricsProvider {
    override val source = LyricsSource.LRCSHARE
    private val details = MetadataEnrichment()
    private var session = ""
    private var sessionKey = ""
    private var expires = 0L
    private fun raw(path: String, headers: Map<String, String>): JsonObject {
        val root = providerJson.parseToJsonElement(http.get("https://api.lrcshare.com/v1$path", headers)) as JsonObject
        check(root.long("code") == 200L) { "LrcShare 接口返回错误" }
        return checkNotNull(root.obj("data")) { "LrcShare 响应缺少数据" }
    }
    @Synchronized private fun headers(): Map<String, String> {
        val key = apiKey().trim()
        if (key.isBlank()) throw ProviderNotConfiguredException("请在插件设置填写 LrcShare API Key。")
        val base = mapOf("X-API-Key" to key, "Accept" to "application/json")
        if (sessionKey != key || expires <= System.currentTimeMillis()) {
            val catalog = raw("/catalog", base)
            session = checkNotNull(catalog.string("session_token")) { "LrcShare 会话获取失败" }
            sessionKey = key; expires = System.currentTimeMillis() + 86_000_000
        }
        return base + ("X-LrcShare-Session" to session)
    }
    private fun get(path: String): JsonObject = raw(path, headers())
    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        val data = get("/search?type=song&keyword=${ProviderHttpClient.encode(keywords)}&limit=${limit.coerceIn(1, 30)}&offset=0")
        return checkNotNull(data.array("items")) { "LrcShare 搜索结果缺失" }.mapNotNull { it.asObject() }.mapNotNull { song ->
            val id = song.string("id") ?: return@mapNotNull null
            val album = song.obj("album")
            LyricsCandidate(source, id, song.string("title").orEmpty(), CandidateMetadata.names(song.array("artists")), album?.string("name").orEmpty(),
                metadata = CandidateMetadata.fields("date" to album?.string("year")) + mapOf(
                    "album_artist" to CandidateMetadata.names(album?.array("artists")), "genre" to song.array("genres").orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }))
        }.filter { it.title.isNotBlank() }.take(limit)
    }
    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = details.apply(candidate) {
        val detail = get("/song/${ProviderHttpClient.encode(candidate.remoteId)}")
        CandidateMetadata.fields("track_number" to detail.string("track"), "disc_number" to detail.string("disc"), "comment" to detail.string("comment")) + mapOf(
            "album_artist" to CandidateMetadata.names(detail.obj("album")?.array("artists")),
            "composer" to strings(detail, "composer"), "lyricist" to strings(detail, "lyricist"))
    }
    private fun strings(value: JsonObject, key: String): List<String> = value.array(key).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val detail = get("/song/${ProviderHttpClient.encode(candidate.remoteId)}?lyric_lines=1")
        val full = if (detail.array("lyric_versions").isNullOrEmpty()) runCatching { get("/song/${ProviderHttpClient.encode(candidate.remoteId)}") }.getOrNull() else null
        LrcShareLyricsCodec.parse(detail, full, source)?.let { LyricsContentFilter.clean(it, candidate) }
    }.getOrNull()
}

internal class ProviderNotConfiguredException(message: String) : IllegalStateException(message)
