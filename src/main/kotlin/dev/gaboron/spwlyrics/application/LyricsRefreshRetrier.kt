package dev.gaboron.spwlyrics.application

/** Retries transient host refresh failures after lyrics have already been persisted. */
internal class LyricsRefreshRetrier(
    private val bridge: LyricsRefreshBridge,
    private val notify: (String) -> Unit,
) {
    fun refresh(isCurrent: () -> Boolean) {
        repeat(REFRESH_ATTEMPTS) { attempt ->
            if (!isCurrent()) return
            if (runCatching(bridge::reloadCurrentLyrics).getOrDefault(false)) return
            if (attempt < REFRESH_ATTEMPTS - 1 && !pause()) return
        }
        if (isCurrent()) notify("歌词已缓存；当前 SPW 版本无法自动刷新，请重新选曲。")
    }

    private fun pause(): Boolean = try {
        Thread.sleep(RETRY_DELAY_MILLIS)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private companion object {
        const val REFRESH_ATTEMPTS = 2
        const val RETRY_DELAY_MILLIS = 150L
    }
}
