package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsPlusJsonCodec
import dev.gaboron.spwlyrics.codec.LyricsScriptConverter
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery

/** Searches Apple Music lyrics through the Apple-only LyricsPlus route. */
class AppleMusicProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.APPLE_MUSIC
    private val catalogSearch = AppleCatalogSearch(http)
    private val codec = LyricsPlusJsonCodec()
    private val documents = object : LinkedHashMap<String, LyricsDocument>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsDocument>?): Boolean =
            size > CACHE_SIZE
    }

    /** Automatic search already performs structured full/basic fallbacks internally. */
    override fun automaticSearchQueries(query: TrackQuery): List<String> = query.searchQueries().take(1)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        automaticSearchRequests(query).firstNotNullOfOrNull { request ->
            runCatching { search(request) }.getOrNull()?.let(::listOf)
        }.orEmpty().take(limit)

    override fun searchManual(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        val results = linkedMapOf<String, LyricsCandidate>()
        for (request in manualSearchRequests(query, keywords, limit).take(MAX_MANUAL_REQUESTS)) {
            if (Thread.currentThread().isInterrupted) break
            runCatching { search(request) }.getOrNull()?.let { results.putIfAbsent(it.remoteId, it) }
            if (results.size >= limit.coerceAtMost(MAX_MANUAL_RESULTS)) break
        }
        return results.values.take(limit.coerceAtMost(MAX_MANUAL_RESULTS))
    }

    private fun search(request: SearchRequest): LyricsCandidate? {
        val payload = codec.decode(http.get(searchUrl(request)), source)
        if (!payload.upstreamSource.orEmpty().contains("apple", ignoreCase = true)) return null
        val document = LyricsScriptConverter.toSimplifiedChinese(payload.document)
        if (document.lines.isEmpty()) return null
        val title = payload.title.orEmpty().ifBlank { request.track }
        val artist = payload.artist.orEmpty().ifBlank { request.artist }
        val album = payload.album.orEmpty().ifBlank { request.album }
        val remoteId = payload.isrc?.takeIf(String::isNotBlank)
            ?: "lyricsplus:${title.lowercase()}|${artist.lowercase()}|${request.durationMs ?: 0}"
        synchronized(documents) { documents[remoteId] = document }
        return LyricsCandidate(
            source = source,
            remoteId = remoteId,
            title = title,
            artists = TrackQuery.splitArtists(artist),
            album = album,
            durationMs = request.durationMs,
            qualityHint = document.quality,
            externalIds = payload.isrc?.let { mapOf("isrc" to it) }.orEmpty(),
            context = mapOf(
                "track" to request.track,
                "artist" to request.artist,
                "album" to request.album,
                "durationMs" to request.durationMs?.toString().orEmpty(),
            ),
        )
    }

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        synchronized(documents) { documents[candidate.remoteId] }?.let { return it }
        val request = SearchRequest(
            track = candidate.context["track"].orEmpty().ifBlank { candidate.title },
            artist = candidate.context["artist"].orEmpty().ifBlank { candidate.artists.joinToString(", ") },
            album = candidate.context["album"].orEmpty().ifBlank { candidate.album },
            durationMs = candidate.context["durationMs"]?.toLongOrNull() ?: candidate.durationMs,
        )
        return runCatching {
            search(request)
            synchronized(documents) { documents[candidate.remoteId] }
        }.getOrNull()
    }

    private fun automaticSearchRequests(query: TrackQuery): List<SearchRequest> {
        val artist = query.artists.joinToString(", ")
        val full = SearchRequest(query.title, artist, query.album, query.durationMs)
        val basic = SearchRequest(query.title, artist)
        return listOf(full, basic).filter { it.track.isNotBlank() && it.artist.isNotBlank() }.distinct()
    }

    private fun manualSearchRequests(query: TrackQuery, keywords: String, limit: Int): List<SearchRequest> {
        val catalog = catalogSearch.search(keywords, limit.coerceAtMost(MAX_CATALOG_RESULTS)).map { track ->
            SearchRequest(track.title, track.artist, track.album, track.durationMs)
        }
        return (catalog + inferManualRequests(keywords, query))
            .filter { it.track.isNotBlank() && it.artist.isNotBlank() }
            .distinct()
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
            "title" to request.track,
            "artist" to request.artist,
            "album" to request.album,
            "source" to "apple",
        )
        request.durationMs?.let { values["duration"] = ((it + 500) / 1_000).toString() }
        return "$SEARCH_URL?" + values.filterValues(String::isNotBlank).entries.joinToString("&") { (key, value) ->
            "$key=${ProviderHttpClient.encode(value)}"
        }
    }

    companion object {
        const val SEARCH_URL = "https://lyricsplus.binimum.org/v2/lyrics/get"
        private const val CACHE_SIZE = 64
        private const val MAX_CATALOG_RESULTS = 6
        private const val MAX_MANUAL_RESULTS = 8
        private const val MAX_MANUAL_REQUESTS = 4
        private const val MAX_MANUAL_PARTS = 8
    }

    private data class SearchRequest(
        val track: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long? = null,
    )
}
