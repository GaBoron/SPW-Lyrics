package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LrcCodec
import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.LyricsTrackMerger
import dev.gaboron.spwlyrics.codec.YrcCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NeteaseMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.NETEASE
    private val details = MetadataEnrichment()

    override fun metadata(candidate: LyricsCandidate) = details.apply(candidate) {
        val id = candidate.context["albumId"]?.toLongOrNull()?.takeIf { it > 0 } ?: return@apply emptyMap()
        val root = providerJson.parseToJsonElement(http.get("https://music.163.com/api/album/$id", HEADERS)) as JsonObject
        check(root.long("code") == 200L)
        val album = checkNotNull(root.obj("album"))
        check(album.long("id") == id)
        mapOf("album_artist" to CandidateMetadata.names(album.array("artists")).ifEmpty {
            listOfNotNull(album.obj("artist")?.string("name"))
        }) + CandidateMetadata.fields("date" to CandidateMetadata.date(album.long("publishTime")))
    }

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        var validResponse = false
        var lastFailure: Throwable? = null
        for (url in SEARCH_URLS) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("NetEase search cancelled")
            val attempt = runCatching { search(url, keywords, limit) }
            attempt.onSuccess { rows ->
                validResponse = true
                if (rows.isNotEmpty()) return rows
            }.onFailure {
                if (it is InterruptedException || Thread.currentThread().isInterrupted) throw it
                lastFailure = it
            }
        }
        if (validResponse) return emptyList()
        throw checkNotNull(lastFailure)
    }

    private fun search(searchUrl: String, keywords: String, limit: Int): List<LyricsCandidate> {
        val url = "$searchUrl?s=${ProviderHttpClient.encode(keywords)}&type=1&offset=0&total=true&limit=$limit"
        val root = providerJson.parseToJsonElement(http.get(url, HEADERS)) as JsonObject
        check(root.long("code") == 200L) { "网易云搜索接口返回错误" }
        val result = checkNotNull(root.obj("result")) { "网易云搜索响应缺少结果" }
        val songs = result.array("songs") ?: if (result.long("songCount") == 0L) emptyList()
            else error("网易云搜索响应缺少候选列表")
        return songs.mapNotNull { element ->
            val song = element.asObject() ?: return@mapNotNull null
            val id = song.long("id")?.toString() ?: song.string("id") ?: return@mapNotNull null
            LyricsCandidate(
                source = source,
                remoteId = id,
                title = song.string("name").orEmpty(),
                artists = (song.array("artists") ?: song.array("ar")).orEmpty().mapNotNull { it.asObject()?.string("name") },
                album = (song.obj("album") ?: song.obj("al"))?.string("name").orEmpty(),
                durationMs = song.long("duration") ?: song.long("dt"),
                qualityHint = LyricsQuality.KARAOKE_SYNCED,
                context = mapOf("albumId" to (song.obj("album") ?: song.obj("al"))?.long("id").toString()),
                metadata = CandidateMetadata.fields("date" to CandidateMetadata.date(song.long("publishTime") ?: song.obj("album")?.long("publishTime")),
                    "track_number" to CandidateMetadata.number(song.string("no")), "disc_number" to CandidateMetadata.number(song.string("cd"))),
            )
        }.also { check(songs.isEmpty() || it.isNotEmpty()) { "网易云搜索候选格式无效" } }
    }

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? =
        runCatching { fetchEapi(candidate) }.getOrNull()
            ?: runCatching { fetchFallback(candidate) }.getOrNull()

    private fun fetchEapi(candidate: LyricsCandidate): LyricsDocument? {
        val data = linkedMapOf(
            "id" to candidate.remoteId, "cp" to "false", "lv" to "-1", "kv" to "-1", "tv" to "-1",
            "rv" to "-1", "yv" to "-1", "ytv" to "-1", "yrv" to "-1", "csrf_token" to "",
        )
        data["header"] = NeteaseEapi.header()
        val response = http.postForm(LYRIC_URL, NeteaseEapi.encrypt(LYRIC_URL, data), HEADERS)
        val root = providerJson.parseToJsonElement(response) as JsonObject
        val original = originalTrack(root, candidate) ?: return null
        val translation = secondaryTrack(root, "ytlrc", "tlyric", candidate)
        val romanization = secondaryTrack(root, "yromalrc", "romalrc", candidate)
        return LyricsContentFilter.clean(LyricsTrackMerger.merge(original, translation, romanization), candidate)
    }

    private fun fetchFallback(candidate: LyricsCandidate): LyricsDocument? {
        val fallback = "$FALLBACK_LYRIC_URL?id=${candidate.remoteId}&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1&ytv=-1&yrv=-1"
        val root = providerJson.parseToJsonElement(http.get(fallback, HEADERS)) as JsonObject
        val original = originalTrack(root, candidate) ?: return null
        return LyricsContentFilter.clean(LyricsTrackMerger.merge(
            original,
            secondaryTrack(root, "ytlrc", "tlyric", candidate),
            secondaryTrack(root, "yromalrc", "romalrc", candidate),
        ), candidate)
    }

    private fun originalTrack(root: JsonObject, candidate: LyricsCandidate): LyricsDocument? =
        root.obj("yrc")?.string("lyric")?.takeIf(String::isNotBlank)
            ?.let { LyricsContentFilter.clean(YrcCodec.parse(it, source), candidate) }
            ?: root.obj("lrc")?.string("lyric")?.takeIf(String::isNotBlank)
                ?.let { LyricsContentFilter.clean(LrcCodec.parse(it, source), candidate) }

    private fun secondaryTrack(root: JsonObject, preferred: String, fallback: String, candidate: LyricsCandidate) =
        listOf(preferred, fallback).firstNotNullOfOrNull { key ->
            root.obj(key)?.string("lyric")?.takeIf(String::isNotBlank)
        }
            ?.let { raw ->
                val parsed = if (raw.lineSequence().any { it.matches(Regex("""^\s*\[\d+,\d+].*""")) }) {
                    YrcCodec.parse(raw, source)
                } else {
                    LrcCodec.parse(raw, source)
                }
                LyricsContentFilter.clean(parsed, candidate)?.lines
            }.orEmpty()

    companion object {
        const val CLOUD_SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
        const val LEGACY_SEARCH_URL = "https://music.163.com/api/search/get"
        private val SEARCH_URLS = listOf(CLOUD_SEARCH_URL, LEGACY_SEARCH_URL)
        const val LYRIC_URL = "https://interface3.music.163.com/eapi/song/lyric/v1"
        const val FALLBACK_LYRIC_URL = "https://music.163.com/api/song/lyric"
        val HEADERS = mapOf("Referer" to "https://music.163.com/")
    }
}

internal object NeteaseEapi {
    private val key = "e82ckenh8dichen8".toByteArray(StandardCharsets.US_ASCII)

    fun encrypt(url: String, values: Map<String, String>): Map<String, String> {
        val apiPath = url.replace("https://interface3.music.163.com/e", "/")
            .replace("https://interface.music.163.com/e", "/")
        val json = buildJsonObject { values.forEach { (key, value) -> put(key, value) } }.toString()
        val digest = MessageDigest.getInstance("MD5")
            .digest("nobody${apiPath}use${json}md5forencrypt".toByteArray(StandardCharsets.UTF_8))
        val digestHex = HexFormat.of().formatHex(digest)
        val message = "$apiPath-36cd479b6b5-$json-36cd479b6b5-$digestHex"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return mapOf("params" to HexFormat.of().withUpperCase().formatHex(cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))))
    }

    fun header(): String = buildJsonObject {
        put("__csrf", "")
        put("appver", "8.0.0")
        put("buildver", (System.currentTimeMillis() / 1_000).toString())
        put("channel", "")
        put("deviceId", "")
        put("mobilename", "")
        put("resolution", "1920x1080")
        put("os", "android")
        put("osver", "")
        put("requestId", "${System.currentTimeMillis()}_0001")
        put("versioncode", "140")
        put("MUSIC_U", "")
    }.toString()
}
