package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.*
import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.JsonObject

/** Qishui search metadata, SEO lyrics, then the public share page as an independent fallback. */
class SodaMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.SODA
    private val details = MetadataEnrichment()
    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        val root = json(http.get("https://api.qishui.com/luna/search/track?aid=386088&device_platform=android&app_name=luna&version_code=100198030&version_name=19.8.0&q=${ProviderHttpClient.encode(keywords)}&cursor=0&count=${limit.coerceIn(1, 50)}", SEARCH_HEADERS))
        check((root.long("status_code") ?: 0) == 0L) { "汽水音乐搜索接口返回错误" }
        val groups = checkNotNull(root.array("result_groups")) { "汽水搜索响应缺少结果" }
        return groups.flatMap { it.asObject()?.array("data").orEmpty() }.mapNotNull {
            it.asObject()?.obj("entity")?.obj("track")?.let(::candidate)
        }.distinctBy { it.remoteId }.take(limit)
    }
    private fun candidate(track: JsonObject): LyricsCandidate? {
        val id = track.string("id") ?: return null
        val title = track.string("name")?.takeIf(String::isNotBlank) ?: return null
        val album = track.obj("album")
        val people = CandidateMetadata.names(track.array("artists"))
        return LyricsCandidate(source, id, title, people, album?.string("name").orEmpty(), track.long("duration"),
            metadata = fields(track))
    }
    private fun fields(track: JsonObject): Map<String, List<String>> {
        val album = track.obj("album")
        val team = track.obj("song_maker_team") ?: track.obj("makerTeam")
        val base = CandidateMetadata.fields("date" to CandidateMetadata.date(track.long("publish_time") ?: album?.long("release_date")),
            "comment" to (track.string("relation_media") ?: track.string("relationMedia"))) + mapOf(
            "composer" to CandidateMetadata.names(team?.array("composers")), "lyricist" to CandidateMetadata.names(team?.array("lyricists")))
        val tags = track.array("tags").orEmpty().mapNotNull { it.asObject() }.groupBy { it.obj("category")?.string("name").orEmpty().lowercase() }
        return base + tags.filterKeys { it in setOf("genre", "language", "copyright") }.mapValues { (_, rows) ->
            rows.flatMap { listOfNotNull(it.obj("first")?.string("name"), it.obj("second")?.string("name")) }.distinct()
        }
    }
    private fun seo(candidate: LyricsCandidate): JsonObject {
        val root = json(http.get("https://beta-luna.douyin.com/luna/h5/seo_track?track_id=${ProviderHttpClient.encode(candidate.remoteId)}&device_platform=web", PAGE_HEADERS))
        check((root.long("status_code") ?: 0) == 0L) { "汽水详情接口返回错误" }
        return root
    }
    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = details.apply(candidate) {
        val root = seo(candidate)
        fields(checkNotNull(root.obj("seo_track")?.obj("track")) { "汽水歌曲详情缺失" })
    }
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        val original = runCatching {
            val root = seo(candidate)
            val lyric = root.obj("seo_track")?.obj("lyric")?.takeIf { !it.string("content").isNullOrBlank() } ?: root.obj("lyric")
            val raw = lyric?.string("content")?.takeIf(String::isNotBlank) ?: return@runCatching null
            val document = OnlineLyricsDecoder.parse(raw, source)
            val translated = lyric.obj("translations")?.string("cn")?.takeIf(String::isNotBlank)?.let { OnlineLyricsDecoder.parse(it, source).lines }.orEmpty()
            LyricsTrackMerger.merge(document, translated, emptyList())
        }.getOrNull() ?: runCatching { share(candidate) }.getOrNull()
        return original?.let { LyricsContentFilter.clean(it, candidate) }
    }
    private fun share(candidate: LyricsCandidate): LyricsDocument {
        val page = http.get("https://music.douyin.com/qishui/share/track?track_id=${ProviderHttpClient.encode(candidate.remoteId)}", PAGE_HEADERS)
        val marker = page.indexOf("_ROUTER_DATA"); check(marker >= 0) { "汽水分享数据缺失" }
        val start = page.indexOf('{', marker); check(start >= 0)
        var depth = 0; var quoted = false; var escaped = false; var end = -1
        for (i in start until page.length) {
            val char = page[i]
            if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
            else if (char == '"') quoted = true else if (char == '{') depth++ else if (char == '}' && --depth == 0) { end = i + 1; break }
        }
        check(end > start)
        val rows = json(page.substring(start, end)).obj("loaderData")?.obj("track_page")?.obj("audioWithLyricsOption")?.obj("lyrics")?.array("sentences").orEmpty()
        val lines = rows.mapNotNull { it.asObject() }.mapNotNull { row ->
            val text = row.array("words").orEmpty().mapNotNull { it.asObject()?.string("text") }.joinToString("")
            text.takeIf(String::isNotBlank)?.let { LyricLine(row.long("startMs"), row.long("endMs"), it) }
        }
        return LyricsDocument(source, LyricsFormat.LRC, lines)
    }
    private fun json(text: String): JsonObject = providerJson.parseToJsonElement(text) as JsonObject
    private companion object {
        val SEARCH_HEADERS = mapOf("User-Agent" to "com.luna.music/100198030 (Linux; U; Android 15; zh_CN_#Hans; ABR-AL80; Build/V417IR;tt-ok/3.12.13.19)", "Accept" to "application/json")
        val PAGE_HEADERS = mapOf("User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 Version/16.6 Mobile/15E148 Safari/604.1")
    }
}
