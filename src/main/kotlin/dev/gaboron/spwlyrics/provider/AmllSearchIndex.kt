package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TextNormalizer
import dev.gaboron.spwlyrics.domain.TrackQuery

/** Immutable metadata search snapshot; manual keywords rank before the playing track. */
internal class AmllSearchIndex(records: List<AmllRecord>) {
    val isEmpty: Boolean get() = entries.isEmpty()
    private val entries = records.map { record ->
        Entry(record, record.titles.map(::searchText),
            (record.titles + record.artists + record.albums).flatMap {
                val text = searchText(it)
                listOf(text, text.replace(" ", ""))
            }.distinct())
    }

    fun search(query: TrackQuery, keywords: String, limit: Int, manual: Boolean): List<LyricsCandidate> {
        if (limit <= 0) return emptyList()
        if (!manual) {
            val exact = entries.filter { entry ->
                query.externalIds.any { (platform, id) ->
                    id.isNotBlank() && entry.record.ids.entries.any { (key, values) ->
                        key.equals(platform, ignoreCase = true) && id in values
                    }
                }
            }
            if (exact.isNotEmpty()) return exact.distinctBy { it.record.identity }.take(limit)
                .map { it.record.candidate(query.title, query.album) }
        }
        val text = searchText(keywords)
        val compact = text.replace(" ", "")
        val tokens = text.split(' ').filter(String::isNotBlank).distinct()
        if (tokens.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { entry -> tokens.all { token -> entry.fields.any { token in it } } }
            .sortedWith(
                compareByDescending<Entry> { entry ->
                    when {
                        entry.titles.any { it.replace(" ", "") == compact } -> 3
                        entry.titles.any { text in it || compact in it.replace(" ", "") } -> 2
                        else -> 1
                    }
                }.thenByDescending { entry ->
                    (entry.record.titles.maxOfOrNull { TextNormalizer.similarity(query.title, it) } ?: 0.0) * 0.7 +
                        TextNormalizer.similarity(query.artists.joinToString(" "), entry.record.artists.joinToString(" ")) * 0.3
                },
            )
            .distinctBy { it.record.identity }
            .take(limit)
            .map { it.record.candidate(if (manual) keywords else query.title, query.album) }
            .toList()
    }

    private data class Entry(val record: AmllRecord, val titles: List<String>, val fields: List<String>)

    private companion object {
        val BRACKETS = Regex("[\\[\\]【】()（）]")
        fun searchText(value: String): String = TextNormalizer.normalize(value.replace(BRACKETS, " "))
    }
}

internal data class AmllRecord(
    val id: String,
    val titles: List<String>,
    val artists: List<String>,
    val albums: List<String>,
    val ids: Map<String, List<String>>,
    val rawLyricFile: String?,
) {
    val identity: String get() = rawLyricFile?.takeIf(String::isNotBlank) ?: id

    fun candidate(titleQuery: String, albumQuery: String) = LyricsCandidate(
        source = LyricsSource.AMLL,
        remoteId = id,
        title = titles.maxByOrNull { TextNormalizer.similarity(titleQuery, it) }.orEmpty(),
        artists = artists,
        album = albums.maxByOrNull { TextNormalizer.similarity(albumQuery, it) }.orEmpty(),
        qualityHint = LyricsQuality.KARAOKE_SYNCED,
        externalIds = ids.mapValues { it.value.firstOrNull().orEmpty() },
        context = mapOf("url" to "${AmllProvider.RAW_BASE}/$id.ttml"),
    )
}
