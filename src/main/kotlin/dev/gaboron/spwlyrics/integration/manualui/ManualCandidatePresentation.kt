package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsQuality
import java.util.Locale

/** Table formatting and sorting are presentation concerns, independent of matching gates. */
internal enum class CandidateColumn(val title: String, val weight: Float = 0f, val width: Int = 0) {
    SOURCE("来源", width = 98), TITLE("歌曲", weight = 1.5f), ARTIST("歌手", weight = 1f),
    ALBUM("专辑", weight = 1.1f), DURATION("时长", width = 48), QUALITY("类型", width = 46), SCORE("匹配分", width = 62);

    fun text(row: CandidateScore): String = when (this) {
        SOURCE -> row.candidate.source.displayName
        TITLE -> row.candidate.title
        ARTIST -> row.candidate.artists.joinToString(" / ")
        ALBUM -> row.candidate.album
        DURATION -> row.candidate.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1_000 % 60) } ?: "—"
        QUALITY -> when (row.candidate.qualityHint) {
            LyricsQuality.KARAOKE_SYNCED -> "逐字"
            LyricsQuality.LINE_SYNCED -> "逐行"
            LyricsQuality.PLAIN -> "普通"
            null -> "未知"
        }
        SCORE -> String.format(Locale.ROOT, "%.3f", row.score)
    }

    fun comparator(): Comparator<CandidateScore> = when (this) {
        SCORE -> compareBy { it.score }
        DURATION -> compareBy { it.candidate.durationMs ?: -1 }
        else -> compareBy { text(it) }
    }
}
