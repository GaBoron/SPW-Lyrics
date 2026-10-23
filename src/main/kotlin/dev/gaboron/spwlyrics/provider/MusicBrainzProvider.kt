package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.*
import kotlinx.serialization.json.JsonObject

/** Recording/release/work IDs remain distinct; this source supplies metadata, not lyrics. */
class MusicBrainzProvider(private val http: ProviderHttp) : LyricsProvider {
    override val source = LyricsSource.MUSICBRAINZ
    private val details = MetadataEnrichment()
    private var lastRequest = 0L
    @Synchronized private fun get(path: String): JsonObject {
        val pause = 1100 - (System.currentTimeMillis() - lastRequest)
        if (pause > 0) Thread.sleep(pause)
        lastRequest = System.currentTimeMillis()
        return providerJson.parseToJsonElement(http.get("https://musicbrainz.org/ws/2/$path", mapOf(
            "Accept" to "application/json", "User-Agent" to "SPW-Lyrics/3.0.0 (https://github.com/GaBoron/SPW-Lyrics)"))) as JsonObject
    }
    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        val phrase = keywords.replace("\\", "\\\\").replace("\"", "\\\"")
        val root = get("recording/?fmt=json&limit=${limit.coerceIn(1, 25)}&query=${ProviderHttpClient.encode(phrase)}")
        return checkNotNull(root.array("recordings")) { "MusicBrainz 搜索结果缺失" }.mapNotNull { it.asObject() }.mapNotNull { recording ->
            val id = recording.string("id") ?: return@mapNotNull null
            val release = recording.array("releases")?.firstOrNull()?.asObject()
            val medium = release?.array("media")?.firstOrNull()?.asObject()
            val track = medium?.array("track")?.firstOrNull()?.asObject()
            LyricsCandidate(source, id, recording.string("title").orEmpty(), credits(recording), release?.string("title").orEmpty(), recording.long("length"),
                context = mapOf("releaseId" to release?.string("id").orEmpty()),
                metadata = CandidateMetadata.fields("date" to (release?.string("date") ?: recording.string("first-release-date")),
                    "track_number" to track?.string("number"), "disc_number" to medium?.long("position")?.toString()) + mapOf("album_artist" to release?.let(::credits).orEmpty()))
        }.filter { it.title.isNotBlank() }.take(limit)
    }
    private fun credits(item: JsonObject): List<String> = item.array("artist-credit").orEmpty().mapNotNull {
        it.asObject()?.let { credit -> credit.string("name") ?: credit.obj("artist")?.string("name") }
    }.distinct()
    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = null
    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = details.apply(candidate) {
        val recording = get("recording/${ProviderHttpClient.encode(candidate.remoteId)}?fmt=json&inc=work-rels+genres")
        check(recording.string("id") == candidate.remoteId) { "MusicBrainz 录音 ID 不匹配" }
        val fields = mutableMapOf("genre" to CandidateMetadata.names(recording.array("genres")))
        val workId = recording.array("relations").orEmpty().mapNotNull { it.asObject()?.obj("work")?.string("id") }.firstOrNull()
        if (workId != null) {
            val work = get("work/${ProviderHttpClient.encode(workId)}?fmt=json&inc=artist-rels")
            check(work.string("id") == workId) { "MusicBrainz 作品 ID 不匹配" }
            val roles = work.array("relations").orEmpty().mapNotNull { it.asObject() }
            fields["composer"] = roles.filter { it.string("type") in listOf("composer", "writer") }.mapNotNull { it.obj("artist")?.string("name") }.distinct()
            fields["lyricist"] = roles.filter { it.string("type") in listOf("lyricist", "writer") }.mapNotNull { it.obj("artist")?.string("name") }.distinct()
        }
        fields
    }
}
