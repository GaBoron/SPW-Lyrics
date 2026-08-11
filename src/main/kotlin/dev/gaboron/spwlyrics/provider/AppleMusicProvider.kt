package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
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

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> = runCatching {
        val root = providerJson.parseToJsonElement(http.get(searchUrl(query, keywords))) as JsonObject
        root.array("results").orEmpty().mapNotNull { element ->
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
    }.getOrDefault(emptyList())

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val url = candidate.context["url"]?.takeIf(::isTrustedLyricsUrl) ?: return@runCatching null
        TtmlCodec().parse(http.get(url), source)
    }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    private fun searchUrl(query: TrackQuery, keywords: String): String {
        val values = linkedMapOf(
            "track" to query.title.ifBlank { keywords },
            "artist" to query.artists.joinToString(", "),
            "album" to query.album,
        )
        query.durationMs?.let { values["duration"] = ((it + 500) / 1_000).toString() }
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
    }
}
