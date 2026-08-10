package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.application.LyricsLoadCoordinator
import dev.gaboron.spwlyrics.application.LyricsResolver
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.AmllProvider
import dev.gaboron.spwlyrics.provider.KugouMusicProvider
import dev.gaboron.spwlyrics.provider.LocalLyricsProvider
import dev.gaboron.spwlyrics.provider.NeteaseMusicProvider
import dev.gaboron.spwlyrics.provider.ProviderHttpClient
import dev.gaboron.spwlyrics.provider.QqMusicProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path

object PluginRuntime {
    @Volatile private var coordinator: LyricsLoadCoordinator? = null

    @Synchronized
    fun install() {
        if (coordinator != null) return
        val localData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            ?.let(::Path) ?: Path(System.getProperty("user.home"))
        val root = localData.resolve("SPW Lyrics")
        val cache = FileLyricsCache(root.resolve("cache"))
        val http = ProviderHttpClient()
        val providers = listOf(
            AmllProvider(root.resolve("amll"), ProviderHttpClient(requestTimeout = Duration.ofSeconds(6))),
            QqMusicProvider(http),
            KugouMusicProvider(http),
            NeteaseMusicProvider(http),
            LocalLyricsProvider(),
        )
        coordinator = LyricsLoadCoordinator(
            cache = cache,
            resolver = LyricsResolver(providers, cache),
            refreshBridge = ReflectiveLyricsRefreshBridge(),
            notify = ::toastWarning,
        )
    }

    fun beforeLoad(mediaItem: PlaybackExtensionPoint.MediaItem): String? = coordinator?.onBeforeLoad(mediaItem.toQuery())
    fun currentQuery(): TrackQuery? = coordinator?.currentQuery()
    fun searchManual(keywords: String, source: LyricsSource?) = coordinator?.searchManual(keywords, source).orEmpty()
    fun preview(candidate: LyricsCandidate) = coordinator?.preview(candidate)
    fun applyManual(candidate: LyricsCandidate): Boolean = coordinator?.applyManual(candidate) == true
    fun useLocal(): Boolean = coordinator?.useLocal() == true

    @Synchronized
    fun close() {
        coordinator?.close()
        coordinator = null
    }

    private fun PlaybackExtensionPoint.MediaItem.toQuery() = TrackQuery(
        title = title,
        artists = TrackQuery.splitArtists(artist),
        album = album,
        albumArtists = TrackQuery.splitArtists(albumArtist),
        path = path,
    )

    private fun toastWarning(message: String) {
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
}
