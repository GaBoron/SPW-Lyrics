package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.codec.LyricsScriptConverter
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TextNormalizer
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.net.URI
import kotlinx.serialization.json.JsonObject

/**
 * Searches the public BiniLyrics Apple Music TTML cache.
 *
 * Apple does not expose lyrics bodies through its public catalog API without a
 * MusicKit user token. Keeping the cache integration here avoids embedding or
 * collecting Apple account credentials in the plugin.
 */
class AppleMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.APPLE_MUSIC

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        searchRequests(query, keywords).firstNotNullOfOrNull { request ->
            runCatching { search(request, limit) }.getOrNull()?.takeIf(List<LyricsCandidate>::isNotEmpty)
        }.orEmpty()

    private fun search(request: SearchRequest, limit: Int): List<LyricsCandidate> {
        val root = providerJson.parseToJsonElement(http.get(searchUrl(request))) as JsonObject
        return root.array("results").orEmpty().mapNotNull { element ->
            val result = element.asObject() ?: return@mapNotNull null
            val lyricsUrl = result.string("lyricsUrl")?.takeIf(::isTrustedLyricsUrl) ?: return@mapNotNull null
            val id = result.string("id") ?: result.string("isrc") ?: return@mapNotNull null
            val timingType = result.string("timing_type").orEmpty()
            LyricsCandidate(
                source = source,
                remoteId = id,
                title = result.string("track_name").orEmpty(),
                artists = result.string("artist_name")?.let(TrackQuery::splitArtists).orEmpty(),
                album = result.string("album_name").orEmpty(),
                durationMs = result.long("duration")?.times(1_000),
                qualityHint = when (timingType.lowercase()) {
                    "word", "syllable" -> LyricsQuality.WORD_SYNCED
                    "line" -> LyricsQuality.LINE_SYNCED
                    else -> null
                },
                externalIds = result.string("isrc")?.let { mapOf("isrc" to it) }.orEmpty(),
                context = mapOf("url" to lyricsUrl),
            )
        }.take(limit)
    }

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val url = candidate.context["url"]?.takeIf(::isTrustedLyricsUrl) ?: return@runCatching null
        LyricsScriptConverter.toSimplifiedChinese(TtmlCodec().parse(http.get(url), source))
    }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    private fun searchRequests(query: TrackQuery, keywords: String): List<SearchRequest> {
        val artist = query.artists.joinToString(", ")
        val full = SearchRequest(query.title, artist, query.album, query.durationMs)
        val basic = SearchRequest(query.title, artist)
        val suppliedKeywords = TextNormalizer.normalize(keywords)
        val automaticKeywords = query.searchQueries().map(TextNormalizer::normalize).toSet()
        val manual = if (suppliedKeywords !in automaticKeywords) inferManualRequests(keywords, query) else emptyList()
        return (manual + full + basic).filter { it.track.isNotBlank() && it.artist.isNotBlank() }.distinct()
    }

    private fun inferManualRequests(keywords: String, query: TrackQuery): List<SearchRequest> {
        val stripped = (query.artists + query.album)
            .filter(String::isNotBlank)
            .fold(keywords) { value, part -> value.replace(part, "", ignoreCase = true) }
            .trim(' ', '-', '–', '—')
        val knownArtist = query.artists.joinToString(", ")
        val withKnownArtist = stripped.takeIf { it.isNotBlank() && knownArtist.isNotBlank() }
            ?.let { listOf(SearchRequest(it, knownArtist)) }.orEmpty()
        val parts = keywords.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(MAX_MANUAL_PARTS)
        val inferred = (1 until parts.size).map { split ->
            SearchRequest(parts.take(split).joinToString(" "), parts.drop(split).joinToString(" "))
        }
        return withKnownArtist + inferred
    }

    private fun searchUrl(request: SearchRequest): String {
        val values = linkedMapOf(
            "track" to request.track,
            "artist" to request.artist,
            "album" to request.album,
        )
        request.durationMs?.let { values["duration"] = ((it + 500) / 1_000).toString() }
        return "$SEARCH_URL?" + values
            .filterValues(String::isNotBlank)
            .entries.joinToString("&") { (key, value) -> "$key=${ProviderHttpClient.encode(value)}" }
    }

    private fun isTrustedLyricsUrl(url: String): Boolean = runCatching {
        val uri = URI.create(url)
        uri.scheme.equals("https", ignoreCase = true) && uri.host.equals(STORAGE_HOST, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        const val SEARCH_URL = "https://lyrics-api.binimum.org/"
        const val STORAGE_HOST = "lyrics-storage.binimum.org"
        private const val MAX_MANUAL_PARTS = 8
    }

    private data class SearchRequest(
        val track: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long? = null,
    )
}
