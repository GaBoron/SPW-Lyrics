package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.util.HexFormat

internal class KugouSearchClient(private val http: ProviderHttp) {
    fun search(keywords: String, limit: Int): List<LyricsCandidate> {
        val time = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf("userid" to "0", "appid" to "3116", "token" to "", "clienttime" to time,
            "iscorrection" to "1", "uuid" to "-", "mid" to md5(time), "dfid" to "-", "clientver" to "11070",
            "platform" to "AndroidFilter", "keyword" to keywords, "page" to "1", "pagesize" to limit.toString())
        val signature = md5(SEARCH_SALT + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + SEARCH_SALT)
        val query = (params + ("signature" to signature)).entries.joinToString("&") { "${it.key}=${ProviderHttpClient.encode(it.value)}" }
        val primary = runCatching { decode(http.get("https://complexsearch.kugou.com/v2/search/song?$query", mapOf(
            "User-Agent" to "Android14-1070-11070-201-0-SearchSong-wifi", "x-router" to "complexsearch.kugou.com"))) }
        primary.onSuccess { if (it.isNotEmpty()) return it.take(limit) }
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Kugou search cancelled")
        val fallback = runCatching { decode(http.get("${KugouMusicProvider.SEARCH_URL}?keyword=${ProviderHttpClient.encode(keywords)}&page=1&pagesize=$limit")) }
        return fallback.getOrElse { primary.getOrThrow() }.take(limit)
    }

    private fun decode(raw: String): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(raw) as JsonObject
        check(root.long("status") == 1L && (root.long("error_code") ?: 0) == 0L) { "酷狗搜索接口返回错误" }
        val rows = checkNotNull(root.obj("data")?.array("lists")) { "酷狗搜索响应缺少候选列表" }
        fun flatten(song: JsonObject): List<JsonObject> = listOf(song) + (song.array("Grp") ?: song.array("grp") ?: song.array("group"))
            .orEmpty().mapNotNull { it.asObject() }.flatMap(::flatten)
        return rows.mapNotNull { it.asObject() }.flatMap(::flatten).mapNotNull { song ->
            val hash = song.string("FileHash")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            LyricsCandidate(source = LyricsSource.KUGOU, remoteId = hash, title = song.string("SongName").orEmpty(),
                artists = CandidateMetadata.names(song.array("Singers")).ifEmpty { TrackQuery.splitArtists(song.string("SingerName").orEmpty()) },
                album = song.string("AlbumName").orEmpty(), durationMs = song.long("Duration")?.times(1000), qualityHint = LyricsQuality.KARAOKE_SYNCED,
                context = mapOf("hash" to hash, "albumAudioId" to song.string("ID").orEmpty(), "albumId" to song.string("AlbumID").orEmpty()),
                metadata = CandidateMetadata.fields("date" to song.string("PublishDate"), "language" to song.obj("trans_param")?.string("language")))
        }.distinctBy { it.remoteId }.also { check(rows.isEmpty() || it.isNotEmpty()) { "酷狗候选格式无效" } }
    }

    companion object {
        private const val SEARCH_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        fun md5(value: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)))
    }
}
