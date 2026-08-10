package com.xuncorp.voxzen.service

import com.xuncorp.pisces.PiscesMediaItem
import com.xuncorp.pisces.PiscesPlayer
import kotlin.coroutines.Continuation

class PlaybackService(private val player: PiscesPlayer) {
    fun activePlayer(): PiscesPlayer = player

    companion object {
        var lastUpdated: PiscesMediaItem? = null

        @JvmStatic
        fun `access$updateLyrics`(
            service: PlaybackService,
            mediaItem: PiscesMediaItem,
            continuation: Continuation<Any?>,
        ) {
            lastUpdated = mediaItem
            continuation.resumeWith(Result.success(Unit))
        }
    }
}
