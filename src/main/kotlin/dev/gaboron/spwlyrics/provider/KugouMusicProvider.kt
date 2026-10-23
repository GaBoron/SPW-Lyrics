package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.KrcCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import kotlinx.serialization.json.JsonObject

class KugouMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.KUGOU
    private val searchClient = KugouSearchClient(http)
    private val details = KugouMetadataClient(http)

    override fun metadata(candidate: LyricsCandidate) = details.load(candidate)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        return searchClient.search(keywords, limit)
    }

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val hash = candidate.context["hash"] ?: candidate.remoteId
        val duration = candidate.durationMs ?: 0L
        val keyword = "${candidate.artists.joinToString("、")} - ${candidate.title}"
        val searchUrl = "$LYRIC_SEARCH?ver=1&man=yes&client=pc&keyword=${ProviderHttpClient.encode(keyword)}&duration=$duration&hash=$hash"
        val root = providerJson.parseToJsonElement(http.get(searchUrl)) as JsonObject
        val lyric = root.array("candidates")?.firstOrNull()?.asObject() ?: return@runCatching null
        val id = lyric.string("id") ?: return@runCatching null
        val key = lyric.string("accesskey") ?: return@runCatching null
        val downloadUrl = "$LYRIC_DOWNLOAD?ver=1&client=pc&id=$id&accesskey=${ProviderHttpClient.encode(key)}&fmt=krc&charset=utf8"
        val downloaded = providerJson.parseToJsonElement(http.get(downloadUrl)) as JsonObject
        val content = downloaded.string("content") ?: return@runCatching null
        LyricsContentFilter.clean(KrcCodec.parse(KrcCodec.decryptBase64(content), source), candidate)
    }.getOrNull()

    companion object {
        const val SEARCH_URL = "https://songsearch.kugou.com/song_search_v2"
        const val LYRIC_SEARCH = "https://lyrics.kugou.com/search"
        const val LYRIC_DOWNLOAD = "https://lyrics.kugou.com/download"
    }
}
