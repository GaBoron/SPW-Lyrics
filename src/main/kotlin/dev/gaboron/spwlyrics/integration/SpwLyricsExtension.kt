package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import org.pf4j.Extension

@Extension
class SpwLyricsExtension : PlaybackExtensionPoint {
    init { discovered = true }

    override fun onSeekTo(position: Long) = PluginRuntime.timingSeeked()

    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? =
        PluginRuntime.beforeLoad(mediaItem)

    override fun onAfterLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? =
        PluginRuntime.afterLocalLyricsMissing(mediaItem)

    companion object {
        // Discovery remains valid across stop/start within the same plugin class loader.
        @Volatile internal var discovered = false
            private set
    }
}
