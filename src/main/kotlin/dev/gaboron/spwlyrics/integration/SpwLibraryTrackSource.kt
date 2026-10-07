package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem
import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/** Keeps library IDs only for the current plugin session; cache keys remain recording based. */
internal class SpwLibraryTrackSource {
    private val byId = ConcurrentHashMap<String, MediaItem>()

    fun load(): List<TrackQuery> {
        requirePermission()
        val tracks = try {
            WorkshopApi.library.getAllTracks().toCompletableFuture().join()
        } catch (error: CompletionException) {
            throw error.cause ?: error
        }
        byId.clear()
        tracks.filter { it.id.isNotBlank() }.forEach { byId[it.id] = it }
        return tracks.asSequence()
            .filter { it.readable && it.title.isNotBlank() && (it.artist.isNotBlank() || it.album.isNotBlank()) }
            .map { it.toQuery() }
            .toList()
    }

    fun fromLyricsCallback(item: MediaItem): TrackQuery {
        val metadata = byId[item.id]?.takeIf { it.path == item.path && it.title == item.title }
            ?: lookupCallbackMetadata(item)
        return item.toQuery(durationOverride = item.duration.takeIf { it > 0 } ?: metadata?.duration)
    }

    fun current(): TrackQuery? {
        requirePermission()
        return WorkshopApi.playback.getCurrentMediaItem()?.also { item ->
            if (item.id.isNotBlank()) byId[item.id] = item
        }?.toQuery()
    }

    fun clear() {
        byId.clear()
    }

    private fun lookupCallbackMetadata(item: MediaItem): MediaItem? {
        if (item.id.isBlank() || item.duration > 0 ||
            !WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)) return null
        val found = runCatching {
            WorkshopApi.library.getTrackById(item.id).toCompletableFuture().join()
        }.getOrNull()?.takeIf { it.path == item.path && it.title == item.title } ?: return null
        byId[item.id] = found
        return found
    }

    private fun requirePermission() {
        if (!WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)) {
            throw PluginPermissionDeniedException("spw-lyrics", PluginPermission.LIBRARY_READ)
        }
    }

    private fun MediaItem.toQuery(durationOverride: Long? = duration) = TrackQuery(
        title = title,
        artists = TrackQuery.splitArtists(artist),
        album = album,
        albumArtists = TrackQuery.splitArtists(albumArtist),
        path = path,
        durationMs = durationOverride?.takeIf { it > 0 },
    )
}
