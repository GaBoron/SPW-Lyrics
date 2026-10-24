package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.*
import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.JsonObject

/** Public catalog metadata with an Apple-ID-based lyric adapter; no account token is embedded. */
class AppleMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.APPLE
    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(http.get("https://itunes.apple.com/search?term=${ProviderHttpClient.encode(keywords)}&entity=song&country=us&limit=${limit.coerceIn(1, 50)}")) as JsonObject
        return checkNotNull(root.array("results")) { "Apple Music 搜索响应缺少结果" }.mapNotNull { it.asObject() }.mapNotNull { song ->
            val id = song.long("trackId")?.toString() ?: return@mapNotNull null
            LyricsCandidate(source, id, song.string("trackName").orEmpty(), TrackQuery.splitArtists(song.string("artistName").orEmpty()),
                song.string("collectionName").orEmpty(), song.long("trackTimeMillis"), metadata = CandidateMetadata.fields(
                    "date" to song.string("releaseDate")?.substringBefore('T'), "genre" to song.string("primaryGenreName"),
                    "track_number" to song.long("trackNumber")?.toString(), "disc_number" to song.long("discNumber")?.toString(),
                    "composer" to song.string("composerName"),
                    "cover_url" to song.string("artworkUrl100")?.replace("/100x100bb.", "/1200x1200bb.")))
        }.filter { it.title.isNotBlank() }.distinctBy { it.remoteId }.take(limit)
    }
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val root = providerJson.parseToJsonElement(http.get("https://lyrics.paxsenix.org/apple-music/lyrics?id=${ProviderHttpClient.encode(candidate.remoteId)}&ttml=false", mapOf("Accept" to "application/json"))) as JsonObject
        val raw = listOf("ttmlContent", "ttml", "elrcMultiPerson", "elrc", "lrc").firstNotNullOfOrNull { key -> root.string(key)?.takeIf(String::isNotBlank) }
        val document = if (raw != null) OnlineLyricsDecoder.parse(raw, source) else {
            val lines = root.array("content").orEmpty().mapNotNull { it.asObject() }.mapNotNull { row ->
                val start = row.long("timestamp") ?: return@mapNotNull null
                val words = row.array("text").orEmpty().mapNotNull { it.asObject() }.mapNotNull { word ->
                    val from = word.long("timestamp") ?: return@mapNotNull null
                    val to = word.long("endtime") ?: return@mapNotNull null
                    val text = word.string("text") ?: return@mapNotNull null
                    LyricWord(from, to, text)
                }
                val text = if (words.isEmpty()) row.string("plain") ?: row.string("text") else words.joinToString("") { it.text }
                text?.takeIf(String::isNotBlank)?.let { LyricLine(start, row.long("endtime"), it, words) }
            }
            LyricsDocument(source, LyricsFormat.LRC, lines)
        }
        LyricsContentFilter.clean(document, candidate)
    }.getOrNull()
    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = candidate.copy(metadata = CandidateMetadata.basic(candidate))
}
