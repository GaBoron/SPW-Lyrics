package dev.gaboron.spwlyrics.provider

import kotlinx.serialization.json.JsonObject

/** Resolves free-form text to structured Apple catalog tracks. */
internal class AppleCatalogSearch(private val http: ProviderHttp) {
    fun search(keywords: String, limit: Int): List<AppleCatalogTrack> = runCatching {
        val url = "$SEARCH_URL?term=${ProviderHttpClient.encode(keywords)}&entity=song&limit=$limit"
        val root = providerJson.parseToJsonElement(http.get(url)) as JsonObject
        root.array("results").orEmpty().mapNotNull { element ->
            val result = element.asObject() ?: return@mapNotNull null
            val title = result.string("trackName").orEmpty()
            val artist = result.string("artistName").orEmpty()
            if (title.isBlank() || artist.isBlank()) return@mapNotNull null
            AppleCatalogTrack(
                title = title,
                artist = artist,
                album = result.string("collectionName").orEmpty(),
                durationMs = result.long("trackTimeMillis"),
            )
        }.distinct()
    }.getOrDefault(emptyList())

    companion object {
        const val SEARCH_URL = "https://itunes.apple.com/search"
    }
}

internal data class AppleCatalogTrack(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
)
