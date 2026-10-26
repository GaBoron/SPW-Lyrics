package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsQuality
import java.util.Locale

/** Candidate labels and sorting stay independent of matching gates and list layout. */
internal enum class CandidateColumn(val title: String) {
    PREFERENCE("设置顺序"), SOURCE("来源"), TITLE("歌曲"), ARTIST("歌手"), ALBUM("专辑"), DURATION("时长"), QUALITY("类型"), SCORE("匹配度");

    fun text(row: CandidateScore): String = when (this) {
        PREFERENCE -> ""
        SOURCE -> row.candidate.source.displayName
        TITLE -> row.candidate.title
        ARTIST -> row.candidate.artists.joinToString(" / ")
        ALBUM -> row.candidate.album
        DURATION -> row.candidate.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1_000 % 60) } ?: "—"
        QUALITY -> when (row.candidate.qualityHint) {
            LyricsQuality.KARAOKE_SYNCED -> "逐字"
            LyricsQuality.LINE_SYNCED -> "逐行"
            LyricsQuality.PLAIN -> "普通"
            null -> if (row.candidate.source.hasLyrics) "点击读取" else "元数据"
        }
        SCORE -> String.format(Locale.ROOT, "%.3f", row.score)
    }

    fun comparator(): Comparator<CandidateScore> = when (this) {
        SCORE -> compareBy { it.score }
        DURATION -> compareBy { it.candidate.durationMs ?: -1 }
        else -> compareBy { text(it) }
    }
}
