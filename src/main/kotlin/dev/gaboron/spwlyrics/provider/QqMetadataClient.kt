package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import kotlinx.serialization.json.*

/** Fetch details only for the selected recording; search latency does not depend on tag enrichment. */
internal class QqMetadataClient(private val http: ProviderHttp) {
    private val enrichment = MetadataEnrichment()
    fun load(candidate: LyricsCandidate) = enrichment.apply(candidate) {
        val albumMid = candidate.context["albumMid"].orEmpty()
        val request = buildJsonObject {
            put("comm", buildJsonObject { put("ct", 19); put("cv", 1873) })
            put("song", buildJsonObject {
                put("module", "music.pf_song_detail_svr"); put("method", "get_song_detail_yqq")
                put("param", buildJsonObject { put("song_type", 0); put("song_mid", candidate.remoteId) })
            })
            if (albumMid.isNotBlank()) put("album", buildJsonObject {
                put("module", "music.musichallAlbum.AlbumInfoServer"); put("method", "GetAlbumDetail")
                put("param", buildJsonObject { put("albumMid", albumMid) })
            })
        }
        val root = providerJson.parseToJsonElement(http.postJson("https://shu6.y.qq.com/cgi-bin/musicu.fcg", request.toString(),
            mapOf("Referer" to "https://y.qq.com/"))) as JsonObject
        check(root.long("code") == 0L)
        val song = checkNotNull(root.obj("song")); check(song.long("code") == 0L)
        val data = checkNotNull(song.obj("data"))
        val info = data.obj("info")
        fun values(key: String) = info?.obj(key)?.array("content").orEmpty().mapNotNull { it.asObject()?.string("value") }
        val album = root.obj("album")?.takeIf { it.long("code") == 0L }?.obj("data")
        mapOf("genre" to values("genre"), "language" to values("lan"),
            "album_artist" to CandidateMetadata.names(album?.obj("singer")?.array("singerList")))
    }
}
