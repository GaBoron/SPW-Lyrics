package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.LyricsRefreshBridge

class ReflectiveLyricsRefreshBridge : LyricsRefreshBridge {
    private val playbackReloader = SpwPlaybackLyricsReloader()

    override fun reloadCurrentLyrics(): Boolean = playbackReloader.reload()
}
