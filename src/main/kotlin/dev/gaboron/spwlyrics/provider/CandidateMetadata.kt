package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneOffset

/** Platform IDs remain in context; only normalized, explicit song fields can become editable tags. */
internal object CandidateMetadata {
    fun fields(vararg values: Pair<String, String?>): Map<String, List<String>> = values.mapNotNull { (key, value) ->
        value?.trim()?.takeIf(String::isNotBlank)?.let { key to listOf(it) }
    }.toMap()
    fun basic(candidate: LyricsCandidate): Map<String, List<String>> = fields("title" to candidate.title, "album" to candidate.album) +
        mapOf("artist" to candidate.artists.filter(String::isNotBlank)) + candidate.metadata
    fun names(rows: List<kotlinx.serialization.json.JsonElement>?, key: String = "name"): List<String> =
        rows.orEmpty().mapNotNull { it.asObject()?.string(key)?.trim()?.takeIf(String::isNotBlank) }.distinct()
    fun date(value: Long?): String? = value?.takeIf { it > 0 }?.let {
        runCatching { Instant.ofEpochMilli(if (it < 100_000_000_000L) it * 1000 else it).atZone(ZoneOffset.UTC).toLocalDate().toString() }.getOrNull()
    }
    fun people(value: String?): List<String> = value.orEmpty().split(Regex("[、/,，;；]|\\s&\\s"))
        .map(String::trim).filter { it.isNotBlank() && !it.matches(Regex("(?i)未知|暂无|无|佚名|unknown|none")) }.distinct()
    fun number(value: String?): String? = value?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()
}

/** Detail failures leave the basic candidate usable; repeated failures cool down per entity. */
internal class MetadataEnrichment(private val now: () -> Long = System::currentTimeMillis) {
    private data class Entry(val fields: Map<String, List<String>>, val expires: Long)
    private val cache = object : LinkedHashMap<String, Entry>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 128
    }
    private val failures = LinkedHashMap<String, Long>()
    fun apply(candidate: LyricsCandidate, fetch: () -> Map<String, List<String>>): LyricsCandidate {
        val key = candidate.remoteId
        val fields = synchronized(this) { cache[key]?.takeIf { it.expires > now() }?.fields }
        if (fields != null) return candidate.copy(metadata = CandidateMetadata.basic(candidate) + fields)
        if (synchronized(this) { (failures[key] ?: 0) > now() }) return candidate.copy(metadata = CandidateMetadata.basic(candidate), metadataNotice = "详情补充暂不可用，已保留基础信息。")
        return try {
            val loaded = fetch().mapValues { (_, values) -> values.map(String::trim).filter(String::isNotBlank).distinct() }.filterValues { it.isNotEmpty() }
            synchronized(this) { cache[key] = Entry(loaded, now() + 604_800_000); failures.remove(key) }
            candidate.copy(metadata = CandidateMetadata.basic(candidate) + loaded)
        } catch (error: Exception) {
            if (error is InterruptedException || Thread.currentThread().isInterrupted) throw error
            synchronized(this) { failures[key] = now() + 120_000; if (failures.size > 128) failures.remove(failures.keys.first()) }
            candidate.copy(metadata = CandidateMetadata.basic(candidate), metadataNotice = "详情补充未完成，已保留基础信息；可稍后重试。")
        }
    }
}
