package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
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
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        val id = ProviderHttpClient.encode(candidate.remoteId)
        // The public TTML cache remains readable when the live lyric service is overloaded.
        // Both paths address the exact catalog ID; never substitute a different recording.
        val paths = listOf("cache/$id", "lyrics?id=$id&ttml=true")
        for (path in paths) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Apple Music request cancelled")
            val document = runCatching {
                val response = http.get(
                    "https://lyrics.paxsenix.org/apple-music/$path",
                    mapOf("Accept" to "application/ttml+xml, application/xml, application/json"),
                )
                decodeTtml(response)?.let { LyricsContentFilter.clean(it, candidate) }
            }.onFailure {
                if (it is InterruptedException || Thread.currentThread().isInterrupted) throw it
            }.getOrNull()
            if (document != null) return document
        }
        return null
    }

    private fun decodeTtml(response: String): LyricsDocument? {
        val raw = response.trim().removePrefix("\uFEFF").trimStart()
        val ttml = if (raw.startsWith("{")) {
            val root = providerJson.parseToJsonElement(raw) as JsonObject
            if (root.string("error") == "true") return null
            listOf("ttmlContent", "ttml").firstNotNullOfOrNull { key ->
                root.string(key)?.takeIf(String::isNotBlank)
            } ?: return null
        } else raw
        // Do not turn an HTTP-200 error page or JSON message into plain lyric text.
        if (!ttml.trimStart().startsWith("<")) return null
        return TtmlCodec().parse(ttml, source)
    }

    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = candidate.copy(metadata = CandidateMetadata.basic(candidate))
}
