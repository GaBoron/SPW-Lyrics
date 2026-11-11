package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.codec.TtmlSourceCredit
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToLong

/** Searches the lyric catalog itself; its recording IDs are ISRCs, not Apple catalog IDs. */
internal class LrcRedClient(private val http: ProviderHttp) {
    fun search(keywords: String, limit: Int): List<LyricsCandidate> {
        if (keywords.isBlank() || limit <= 0) return emptyList()
        val response = http.get("$BASE/search.json?q=${ProviderHttpClient.encode(keywords)}", HEADERS)
        val root = providerJson.parseToJsonElement(response) as JsonObject
        return checkNotNull(root.array("hits")) { "lrc.red 搜索响应缺少结果" }
            .mapNotNull { it.asObject() }
            .mapNotNull { song ->
                val isrc = song.string("isrc")?.takeIf(ISRC::matches) ?: return@mapNotNull null
                val title = song.string("title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val artist = song.string("artist")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val duration = song.string("duration")?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it > 0 && it < 86_400 }?.let { (it * 1000).roundToLong() }
                LyricsCandidate(
                    source = LyricsSource.APPLE,
                    remoteId = "$ID_PREFIX$isrc",
                    title = title,
                    artists = TrackQuery.splitArtists(artist),
                    album = song.string("album").orEmpty(),
                    durationMs = duration,
                    externalIds = mapOf("isrc" to isrc),
                    metadata = CandidateMetadata.fields(
                        "date" to song.string("year"),
                        "cover_url" to "$BASE/s/$isrc.webp",
                        "source" to "lrc.red",
                        "source_url" to "$BASE/s/$isrc",
                    ),
                )
            }.distinctBy { it.remoteId }.take(limit)
    }

    fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        val isrc = candidate.remoteId.removePrefix(ID_PREFIX)
        if (!candidate.remoteId.startsWith(ID_PREFIX) || !ISRC.matches(isrc)) return null
        val raw = http.get("$BASE/s/$isrc.ttml", HEADERS)
        val document = LyricsContentFilter.clean(TtmlCodec().parse(raw, LyricsSource.APPLE), candidate) ?: return null
        // Attribution travels with playback, TTML handoff and exported lyrics, including offline use.
        return TtmlSourceCredit.append(document, "lrc.red", "$BASE/s/$isrc")
    }

    companion object {
        const val ID_PREFIX = "lrc.red:"
        private const val BASE = "https://lrc.red"
        private val ISRC = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
        private val HEADERS = mapOf(
            "User-Agent" to "SPW-Lyrics (https://github.com/GaBoron/SPW-Lyrics; gaboron081@gmail.com)",
            "Accept" to "application/json, application/ttml+xml, application/xml",
        )
    }
}
