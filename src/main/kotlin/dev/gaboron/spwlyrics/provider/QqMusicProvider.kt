package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsTrackMerger
import dev.gaboron.spwlyrics.codec.QrcCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class QqMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.QQ

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> = runCatching {
        val request = """{"req_1":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":{"query":${providerJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(keywords))},"num_per_page":$limit,"page_num":1,"search_type":0}},"comm":{"ct":"19","cv":"1859"}}"""
        val root = providerJson.parseToJsonElement(http.postJson(SEARCH_URL, request)) as JsonObject
        root.obj("req_1")?.obj("data")?.obj("body")?.obj("song")?.array("list").orEmpty().mapNotNull { element ->
            val song = element.asObject() ?: return@mapNotNull null
            val title = song.string("title") ?: song.string("name") ?: return@mapNotNull null
            val singers = song.array("singer").orEmpty().mapNotNull { it.asObject()?.string("name") }
            LyricsCandidate(
                source = source,
                remoteId = song.string("mid").orEmpty(),
                title = title,
                artists = singers,
                album = song.obj("album")?.string("name").orEmpty(),
                durationMs = song.long("interval")?.times(1_000),
                qualityHint = LyricsQuality.WORD_SYNCED,
                context = mapOf("musicId" to (song.long("id")?.toString().orEmpty())),
            )
        }
    }.getOrDefault(emptyList())

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val musicId = candidate.context["musicId"]?.takeIf(String::isNotBlank) ?: return@runCatching null
        val url = "$LYRIC_URL?version=15&miniversion=82&lrctype=4&musicid=$musicId"
        val xml = http.get(url, mapOf("Referer" to "https://y.qq.com/"))
        val values = extractQqLyricValues(xml)
        val original = values["content"]?.takeIf(String::isNotBlank)
            ?.let { QrcCodec.parse(QrcCodec.decryptHex(it), source) }
            ?: return@runCatching null
        val translation = values["contentts"]?.takeIf(String::isNotBlank)
            ?.let { runCatching { QrcCodec.parse(QrcCodec.decryptHex(it), source).lines }.getOrNull() }.orEmpty()
        val romanization = values["contentroma"]?.takeIf(String::isNotBlank)
            ?.let { runCatching { QrcCodec.parse(QrcCodec.decryptHex(it), source).lines }.getOrNull() }.orEmpty()
        LyricsTrackMerger.merge(original, translation, romanization)
    }.getOrNull()

    companion object {
        const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val LYRIC_URL = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg"
    }
}

internal fun extractQqLyricValues(xml: String): Map<String, String> =
    Regex("<(content|contentts|contentroma)\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        .findAll(xml)
        .associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
                .removePrefix("<![CDATA[")
                .removeSuffix("]]>")
                .trim()
        }
