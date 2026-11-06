package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LrcShareLyricsCodec
import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.domain.*
import java.nio.file.Path
import java.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Uses the plugin's program-scoped credential and a locally cached catalog-issued session. */
class LrcShareProvider(
    http: ProviderHttpClient,
    cacheDirectory: Path,
    clock: Clock = Clock.systemUTC(),
) : LyricsProvider, AutoCloseable {
    override val source = LyricsSource.LRCSHARE
    private val details = MetadataEnrichment()
    override fun configurationStatus(): ProviderSearchStatus? = if (LrcShareAccess.available) null else
        ProviderSearchStatus(source, ProviderSearchPhase.NOT_CONFIGURED, "此构建未包含 LrcShare 凭据，请使用正式安装包。")
    private val client = LrcShareClient(http, cacheDirectory, clock)
    private val manual = LrcShareManualSearch(client::searchKeywords)

    override fun automaticSearchQueries(query: TrackQuery): List<String> =
        listOf(query.title, TextNormalizer.removeVersionNoise(query.title)).map(String::trim).filter(String::isNotBlank).distinct()

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        if (keywords.isBlank()) return emptyList()
        val matched = candidates(client.search(keywords, query.artists, limit), limit)
        // Retain title-only recovery for spelling/alias differences in the track's artist tags.
        return if (matched.isEmpty() && query.artists.any(String::isNotBlank)) {
            candidates(client.search(keywords, emptyList(), limit), limit)
        } else matched
    }

    override fun searchManual(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        candidates(client.searchKeywords(keywords, limit), limit)

    override fun searchManual(query: TrackQuery, keywords: String, limit: Int,
        onStatus: (ProviderSearchStatus) -> Unit): List<LyricsCandidate> {
        val result = manual.search(keywords, limit, onStatus) ?: return emptyList()
        val data = result.data
        val candidates = candidates(data, limit)
        val detail = when {
            data == null -> "本地目录中没有匹配文本，已跳过在线搜索。"
            candidates.isEmpty() -> "请求成功，没有匹配候选。"
            else -> "请求成功，找到 ${candidates.size} 个候选。"
        }
        onStatus(ProviderSearchStatus(source, ProviderSearchPhase.AVAILABLE, detail))
        return candidates
    }

    private fun candidates(data: JsonObject?, limit: Int): List<LyricsCandidate> {
        if (data == null) return emptyList()
        return checkNotNull(data.array("items")) { "LrcShare 搜索结果缺失" }.mapNotNull { it.asObject() }.mapNotNull { song ->
            val id = song.string("id") ?: return@mapNotNull null
            val album = song.obj("album")
            LyricsCandidate(
                source = source,
                remoteId = id,
                title = song.string("title").orEmpty(),
                artists = CandidateMetadata.names(song.array("artists")),
                album = album?.string("name").orEmpty(),
                metadata = CandidateMetadata.fields(
                    "date" to album?.string("year"),
                    "cover_url" to album?.string("cover_url"),
                ) + mapOf(
                    "album_artist" to CandidateMetadata.names(album?.array("artists")),
                    "genre" to strings(song, "genres"),
                ),
            )
        }.filter { it.title.isNotBlank() }.take(limit)
    }

    override fun metadata(candidate: LyricsCandidate): LyricsCandidate = details.apply(candidate) {
        val detail = client.get("/song/${ProviderHttpClient.encode(candidate.remoteId)}")
        CandidateMetadata.fields(
            "track_number" to detail.string("track"),
            "disc_number" to detail.string("disc"),
            "comment" to detail.string("comment"),
            "cover_url" to detail.obj("album")?.string("cover_url"),
        ) + mapOf(
            "album_artist" to CandidateMetadata.names(detail.obj("album")?.array("artists")),
            "composer" to strings(detail, "composer"),
            "lyricist" to strings(detail, "lyricist"),
        )
    }

    private fun strings(value: JsonObject, key: String): List<String> =
        value.array(key).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val path = "/song/${ProviderHttpClient.encode(candidate.remoteId)}"
        val detail = client.get("$path?lyric_lines=1")
        val full = if (detail.array("lyric_versions").isNullOrEmpty()) runCatching {
            client.get(path)
        }.onFailure { if (it is InterruptedException || Thread.currentThread().isInterrupted) throw it }.getOrNull() else null
        LrcShareLyricsCodec.parse(detail, full, source)?.let { LyricsContentFilter.clean(it, candidate) }
    }.onFailure { if (it is InterruptedException || Thread.currentThread().isInterrupted) throw it }.getOrNull()

    override fun close() = manual.close()
}
