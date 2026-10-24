package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.AudioArtwork
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.OnlineMetadataImport
import dev.gaboron.spwlyrics.provider.CoverArtworkClient

/** An unavailable image must not discard the selected candidate's text metadata. */
internal class OnlineArtworkImport(private val download: (String) -> AudioArtwork = CoverArtworkClient()::load) {
    fun prepare(candidate: LyricsCandidate) = retry(OnlineMetadataImport.from(candidate))

    fun retry(pending: OnlineMetadataImport): OnlineMetadataImport {
        val url = pending.coverUrl ?: return pending.copy(coverNotice = "此来源未提供封面，可更换候选或选择本地图片。")
        return try {
            pending.copy(cover = download(url), coverNotice = null)
        } catch (error: Exception) {
            if (error is InterruptedException || Thread.currentThread().isInterrupted) throw error
            pending.copy(cover = null, coverNotice = "封面读取失败：${error.message ?: "请检查网络"}；其他在线字段仍可导入。")
        }
    }
}
