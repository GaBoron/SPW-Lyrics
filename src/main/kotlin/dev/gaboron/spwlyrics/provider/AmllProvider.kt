package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TextNormalizer
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Clock
import java.time.Duration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AmllProvider(
    private val root: Path,
    private val http: ProviderHttp,
    private val clock: Clock = Clock.systemUTC(),
) : LyricsProvider {
    override val source: LyricsSource = LyricsSource.AMLL
    private val index = AmllIndexStore(root, http, clock)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        index.search(query, keywords, limit)

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val url = candidate.context["url"] ?: "$RAW_BASE/${candidate.remoteId}.ttml"
        TtmlCodec().parse(http.get(url), source)
    }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    companion object {
        const val INDEX_URL = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics/index.jsonl"
        const val RAW_BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics"
    }
}

internal class AmllIndexStore(
    private val root: Path,
    private val http: ProviderHttp,
    private val clock: Clock,
) {
    private val indexPath = root.resolve("amll-index.jsonl")
    @Volatile private var records: List<AmllRecord>? = null
    @Volatile private var inverted: Map<String, Set<Int>>? = null
    @Volatile private var byExternalId: Map<String, Set<Int>>? = null

    fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
        ensureLoaded()
        val all = records.orEmpty()
        if (all.isEmpty()) return emptyList()
        val exactIds = query.externalIds.values.flatMap { byExternalId.orEmpty()[it].orEmpty() }.distinct()
        if (exactIds.isNotEmpty()) return exactIds.mapNotNull(all::getOrNull).take(limit).map(AmllRecord::candidate)
        val tokens = TextNormalizer.normalize(keywords).split(' ').filter { it.length >= 2 }.toSet()
        val ids = tokens.flatMap { inverted.orEmpty()[it].orEmpty() }.distinct()
        val pool = if (ids.isNotEmpty()) ids.mapNotNull(all::getOrNull) else emptyList()
        return pool.asSequence()
            .sortedByDescending { record ->
                TextNormalizer.similarity(query.title, record.title) * 0.7 +
                    TextNormalizer.similarity(query.artists.joinToString(" "), record.artists.joinToString(" ")) * 0.3
            }
            .take(limit)
            .map(AmllRecord::candidate)
            .toList()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (records != null) return
        Files.createDirectories(root)
        val stale = !Files.isRegularFile(indexPath) ||
            Files.getLastModifiedTime(indexPath).toMillis() + Duration.ofDays(1).toMillis() < clock.millis()
        if (stale) refresh()
        val loaded = if (Files.isRegularFile(indexPath)) {
            Files.readAllLines(indexPath, StandardCharsets.UTF_8).mapNotNull(::parseRecord)
        } else emptyList()
        records = loaded
        inverted = buildMap {
            loaded.forEachIndexed { index, record ->
                val tokens = TextNormalizer.normalize(
                    listOf(record.title, record.artists.joinToString(" "), record.album).joinToString(" "),
                ).split(' ').filter { it.length >= 2 }.toSet()
                tokens.forEach { token -> put(token, getOrDefault(token, emptySet()) + index) }
            }
        }
        byExternalId = buildMap {
            loaded.forEachIndexed { index, record ->
                record.ids.values.flatten().forEach { id -> put(id, getOrDefault(id, emptySet()) + index) }
            }
        }
    }

    private fun refresh() {
        runCatching {
            val content = http.get(AmllProvider.INDEX_URL)
            val temp = Files.createTempFile(root, "amll-index", ".tmp")
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            try {
                Files.move(temp, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, indexPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun parseRecord(line: String): AmllRecord? = runCatching {
        val root = providerJson.parseToJsonElement(line) as JsonObject
        val metadata = (root["metadata"] as? JsonArray).orEmpty().associate { pairElement ->
            val pair = pairElement as JsonArray
            val key = (pair[0] as JsonPrimitive).content
            val values = (pair[1] as JsonArray).map { (it as JsonPrimitive).content }
            key to values
        }
        val id = root.string("id") ?: return@runCatching null
        AmllRecord(
            id = id,
            title = metadata["musicName"]?.firstOrNull().orEmpty(),
            artists = metadata["artists"].orEmpty(),
            album = metadata["album"]?.firstOrNull().orEmpty(),
            ids = metadata.filterKeys { it.endsWith("Id", true) },
        )
    }.getOrNull()
}

private data class AmllRecord(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val ids: Map<String, List<String>>,
) {
    fun candidate() = LyricsCandidate(
        source = LyricsSource.AMLL,
        remoteId = id,
        title = title,
        artists = artists,
        album = album,
        qualityHint = LyricsQuality.WORD_SYNCED,
        externalIds = ids.mapValues { it.value.firstOrNull().orEmpty() },
        context = mapOf("url" to "${AmllProvider.RAW_BASE}/$id.ttml"),
    )
}
