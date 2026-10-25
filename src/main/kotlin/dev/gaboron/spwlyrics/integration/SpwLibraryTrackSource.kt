package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem
import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Keeps library IDs only for the current plugin session; cache keys remain recording based. */
internal class SpwLibraryTrackSource {
    private val byId = ConcurrentHashMap<String, MediaItem>()

    fun load(): List<TrackQuery> = load(timeoutSeconds = null)

    fun loadForPrecache(): List<TrackQuery> = load(timeoutSeconds = 5)

    private fun load(timeoutSeconds: Long?): List<TrackQuery> {
        requirePermission()
        val tracks = try {
            val future = WorkshopApi.library.getAllTracks().toCompletableFuture()
            if (timeoutSeconds == null) future.join() else future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (error: CompletionException) {
            throw error.cause ?: error
        } catch (error: ExecutionException) {
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
        return item.toQuery(durationOverride = item.duration.takeIf { it > 0 } ?: metadata?.duration)
    }

    /** Only resolution workers may wait for library metadata; cache hits never need it. */
    fun prepare(query: TrackQuery): TrackQuery {
        if (query.durationMs != null || query.trackId.isBlank()) return query
        val cached = byId[query.trackId]?.takeIf { it.path == query.path && it.title == query.title }
        val found = cached ?: lookupMetadata(query) ?: return query
        return query.copy(durationMs = found.duration.takeIf { it > 0 })
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

    private fun lookupMetadata(query: TrackQuery): MediaItem? {
        if (query.trackId.isBlank() ||
            !WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)) return null
        val found = runCatching {
            WorkshopApi.library.getTrackById(query.trackId).toCompletableFuture().get(2, TimeUnit.SECONDS)
        }.getOrNull()?.takeIf { it.path == query.path && it.title == query.title } ?: return null
        byId[query.trackId] = found
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
        trackId = id,
    )
}
