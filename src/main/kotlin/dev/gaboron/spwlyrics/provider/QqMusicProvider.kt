package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.LyricsTrackMerger
import dev.gaboron.spwlyrics.codec.QrcCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery

class QqMusicProvider(private val http: ProviderHttp) : LyricsProvider, AutoCloseable {
    override val source = LyricsSource.QQ
    private val searchClient = QqSearchClient(http)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        searchClient.search(keywords, limit)

    override fun close() = searchClient.close()

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val musicId = candidate.context["musicId"]?.takeIf(String::isNotBlank) ?: return@runCatching null
        val url = "$LYRIC_URL?version=15&miniversion=82&lrctype=4&musicid=$musicId"
        val xml = http.get(url, mapOf("Referer" to "https://y.qq.com/"))
        val values = extractQqLyricValues(xml)
        val original = values["content"]?.takeIf(String::isNotBlank)
            ?.let(::decodeTrack)
            ?.let { LyricsContentFilter.clean(it, candidate) }
            ?: return@runCatching null
        val translation = values["contentts"]?.takeIf(String::isNotBlank)
            ?.let { runCatching { LyricsContentFilter.clean(decodeTrack(it), candidate)?.lines }.getOrNull() }.orEmpty()
        val romanization = values["contentroma"]?.takeIf(String::isNotBlank)
            ?.let { runCatching { LyricsContentFilter.clean(decodeTrack(it), candidate)?.lines }.getOrNull() }.orEmpty()
        LyricsContentFilter.clean(LyricsTrackMerger.merge(original, translation, romanization), candidate)
    }.getOrNull()

    private fun decodeTrack(content: String): LyricsDocument {
        val raw = content.trim()
        val decoded = if (raw.length % 2 == 0 && raw.matches(HEX_PAYLOAD)) {
            QrcCodec.decryptHex(raw)
        } else {
            raw
        }
        return QrcCodec.parse(decoded, source)
    }

    companion object {
        const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val LYRIC_URL = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg"
        private val HEX_PAYLOAD = Regex("^[0-9a-fA-F]+$")
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
