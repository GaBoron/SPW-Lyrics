package dev.gaboron.spwlyrics.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class TrackQuery(
    val title: String,
    val artists: List<String>,
    val album: String,
    val albumArtists: List<String> = emptyList(),
    val path: String = "",
    val durationMs: Long? = null,
    val externalIds: Map<String, String> = emptyMap(),
) {
    val key: String
        get() {
            val identity = listOf(
                path.trim().lowercase(),
                TextNormalizer.normalize(title),
                artists.joinToString("/") { TextNormalizer.normalize(it) },
                TextNormalizer.normalize(album),
            ).joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

    fun searchQueries(): List<String> {
        val cleanTitle = TextNormalizer.removeVersionNoise(title).trim()
        val artistText = artists.joinToString(" ")
        return buildList {
            add(listOf(title, artistText, album).filter(String::isNotBlank).joinToString(" "))
            add(listOf(title, artistText).filter(String::isNotBlank).joinToString(" "))
            add(listOf(cleanTitle, artistText).filter(String::isNotBlank).joinToString(" "))
            add(cleanTitle)
        }.map(String::trim).filter(String::isNotBlank).distinct()
    }

    companion object {
        fun splitArtists(value: String): List<String> = TextNormalizer.splitArtists(value)
    }
}

@Serializable
data class LyricsCandidate(
    val source: LyricsSource,
    val remoteId: String,
    val title: String,
    val artists: List<String>,
    val album: String = "",
    val durationMs: Long? = null,
    val qualityHint: LyricsQuality? = null,
    val externalIds: Map<String, String> = emptyMap(),
    val context: Map<String, String> = emptyMap(),
)
