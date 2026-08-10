package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LrcCodec
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

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> = runCatching {
        val url = "$SEARCH_URL?s=${ProviderHttpClient.encode(keywords)}&type=1&offset=0&total=true&limit=$limit"
        val root = providerJson.parseToJsonElement(http.get(url, HEADERS)) as JsonObject
        root.obj("result")?.array("songs").orEmpty().mapNotNull { element ->
            val song = element.asObject() ?: return@mapNotNull null
            val id = song.long("id")?.toString() ?: song.string("id") ?: return@mapNotNull null
            LyricsCandidate(
                source = source,
                remoteId = id,
                title = song.string("name").orEmpty(),
                artists = (song.array("artists") ?: song.array("ar")).orEmpty().mapNotNull { it.asObject()?.string("name") },
                album = (song.obj("album") ?: song.obj("al"))?.string("name").orEmpty(),
                durationMs = song.long("duration") ?: song.long("dt"),
                qualityHint = LyricsQuality.WORD_SYNCED,
            )
        }
    }.getOrDefault(emptyList())

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val data = linkedMapOf(
            "id" to candidate.remoteId, "cp" to "false", "lv" to "0", "kv" to "0", "tv" to "0",
            "rv" to "0", "yv" to "0", "ytv" to "0", "yrv" to "0", "csrf_token" to "",
        )
        data["header"] = NeteaseEapi.header()
        val response = http.postForm(LYRIC_URL, NeteaseEapi.encrypt(LYRIC_URL, data), HEADERS)
        val root = providerJson.parseToJsonElement(response) as JsonObject
        val yrc = root.obj("yrc")?.string("lyric")
        val lrc = root.obj("lrc")?.string("lyric")
        val original = when {
            !yrc.isNullOrBlank() -> YrcCodec.parse(yrc, source)
            !lrc.isNullOrBlank() -> LrcCodec.parse(lrc, source)
            else -> return@runCatching null
        }
        val translation = secondaryTrack(root, "ytlrc", "tlyric")
        val romanization = secondaryTrack(root, "yromalrc", "romalrc")
        LyricsTrackMerger.merge(original, translation, romanization)
    }.recoverCatching {
        val fallback = "$FALLBACK_LYRIC_URL?id=${candidate.remoteId}&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1"
        val root = providerJson.parseToJsonElement(http.get(fallback, HEADERS)) as JsonObject
        val yrc = root.obj("yrc")?.string("lyric")
        val original = if (yrc.isNullOrBlank()) {
            LrcCodec.parse(root.obj("lrc")?.string("lyric").orEmpty(), source)
        } else {
            YrcCodec.parse(yrc, source)
        }
        LyricsTrackMerger.merge(
            original,
            secondaryTrack(root, "ytlrc", "tlyric"),
            secondaryTrack(root, "yromalrc", "romalrc"),
        )
    }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    private fun secondaryTrack(root: JsonObject, preferred: String, fallback: String) =
        (root.obj(preferred)?.string("lyric") ?: root.obj(fallback)?.string("lyric"))
            ?.takeIf(String::isNotBlank)
            ?.let { raw ->
                if (raw.lineSequence().any { it.matches(Regex("""^\s*\[\d+,\d+].*""")) }) {
                    YrcCodec.parse(raw, source).lines
                } else {
                    LrcCodec.parse(raw, source).lines
                }
            }.orEmpty()

    companion object {
        const val SEARCH_URL = "https://music.163.com/api/search/get/web"
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
