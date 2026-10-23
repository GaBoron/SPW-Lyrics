package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import kotlinx.serialization.json.*

internal class KugouMetadataClient(private val http: ProviderHttp) {
    private val enrichment = MetadataEnrichment()
    fun load(candidate: LyricsCandidate) = enrichment.apply(candidate) {
        val fields = mutableMapOf<String, List<String>>()
        candidate.context["albumAudioId"]?.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
            val row = request("audio", "238", "entity_id", id, "base,extra,tags").firstOrNull {
                it.obj("base")?.long("album_audio_id") == id
            } ?: error("酷狗歌曲详情不匹配")
            val extra = row.obj("extra")
            fields += CandidateMetadata.fields("language" to row.obj("base")?.string("language"),
                "track_number" to CandidateMetadata.number(extra?.string("sort")), "disc_number" to CandidateMetadata.number(extra?.string("disc")))
            fields["composer"] = CandidateMetadata.people(extra?.string("composer"))
            fields["lyricist"] = CandidateMetadata.people(extra?.string("lyrics"))
        }
        candidate.context["albumId"]?.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
            val album = request("albums", "255", "album_id", id, "album_id,authors").firstOrNull { it.long("album_id") == id }
                ?: error("酷狗专辑详情不匹配")
            fields["album_artist"] = CandidateMetadata.names(album.array("authors"), "author_name")
        }
        fields
    }

    private fun request(path: String, tid: String, idKey: String, id: Long, fields: String): List<JsonObject> {
        val time = (System.currentTimeMillis() / 1000).toString()
        val mid = KugouSearchClient.md5(time)
        val params = mapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489", "clienttime" to time)
        val body = buildJsonObject {
            put("data", buildJsonArray { add(buildJsonObject { put(idKey, id) }) }); put("fields", fields)
            if (path == "albums") put("is_buy", 0)
        }.toString()
        val salt = "OIlwieks28dk2k092lksi2UIkp"
        val sign = KugouSearchClient.md5(salt + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + body + salt)
        val query = (params + ("signature" to sign)).entries.joinToString("&") { "${it.key}=${ProviderHttpClient.encode(it.value)}" }
        val root = providerJson.parseToJsonElement(http.postJson("https://gateway.kugou.com/kmr/v2/$path?$query", body, mapOf(
            "x-router" to "openapi.kugou.com", "KG-TID" to tid, "dfid" to "-", "mid" to mid, "clienttime" to time,
            "User-Agent" to "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"))) as JsonObject
        check((root.long("error_code") ?: 0) == 0L)
        return checkNotNull(root.array("data")).mapNotNull { it.asObject() }
    }
}
