package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.TextNormalizer
import dev.gaboron.spwlyrics.domain.TrackQuery

/** Supplies both local and confirmed primary-source metadata when providers use different regional names. */
internal object TranslationLookupPlan {
    fun queries(local: TrackQuery, primary: LyricsCandidate): List<TrackQuery> {
        val primaryQuery = TrackQuery(
            title = primary.title.ifBlank { local.title },
            artists = primary.artists.ifEmpty { local.artists },
            album = primary.album.ifBlank { local.album },
            albumArtists = local.albumArtists,
            durationMs = local.durationMs ?: primary.durationMs,
            externalIds = local.externalIds + primary.externalIds,
        )
        return listOf(local, primaryQuery).distinctBy { query ->
            listOf(
                TextNormalizer.compact(query.title),
                query.artists.joinToString("/") { TextNormalizer.compact(it) },
                TextNormalizer.compact(query.album),
                query.durationMs?.toString().orEmpty(),
            ).joinToString("|")
        }
    }
}
