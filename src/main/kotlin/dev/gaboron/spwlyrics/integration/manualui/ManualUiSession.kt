package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.application.ResolvedLyrics
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.util.concurrent.ConcurrentHashMap

class ManualUiSession(
    private val currentQuery: () -> TrackQuery?,
    private val search: (String, LyricsSource?) -> List<CandidateScore>,
    private val preview: (LyricsCandidate) -> ResolvedLyrics?,
    private val apply: (LyricsCandidate) -> Boolean,
    private val useLocal: () -> Boolean,
    private val useAutomatic: () -> Boolean,
) {
    private val candidates = ConcurrentHashMap<String, LyricsCandidate>()

    fun handle(request: ManualUiRequest): ManualUiResponse = when (request.action) {
        "state" -> state()
        "search" -> search(request)
        "preview" -> candidate(request)?.let(::previewResponse)
            ?: ManualUiResponse(false, "候选已失效，请重新搜索。")
        "apply" -> candidate(request)?.let {
            val applied = apply(it)
            ManualUiResponse(applied, if (applied) "歌词已应用。" else "该候选没有可用歌词。")
        } ?: ManualUiResponse(false, "候选已失效，请重新搜索。")
        "local" -> useLocal().let { applied ->
            ManualUiResponse(applied, if (applied) "已切回 SPW 本地歌词流程。" else "当前没有可切换的歌曲。")
        }
        "automatic" -> useAutomatic().let { applied ->
            ManualUiResponse(applied, if (applied) "已清除手动锁定，正在重新自动匹配。" else "当前没有可切换的歌曲。")
        }
        else -> ManualUiResponse(false, "未知请求。")
    }

    private fun state(): ManualUiResponse {
        val query = currentQuery() ?: return ManualUiResponse(false, "请先在 SPW 中播放一首歌曲。", sources = sources())
        return ManualUiResponse(
            ok = true,
            track = ManualUiTrack(
                title = query.title,
                artists = query.artists.joinToString(" / "),
                album = query.album,
                suggestedKeywords = query.searchQueries().firstOrNull().orEmpty(),
            ),
            sources = sources(),
        )
    }

    private fun search(request: ManualUiRequest): ManualUiResponse {
        val keywords = request.keywords.orEmpty().trim()
        if (keywords.isEmpty()) return ManualUiResponse(false, "请输入搜索关键词。")
        val source = request.source?.let { runCatching { LyricsSource.valueOf(it) }.getOrNull() }
        val rows = search(keywords, source)
        candidates.clear()
        val mapped = rows.map { row ->
            val candidate = row.candidate
            val key = "${candidate.source.name}:${candidate.remoteId}"
            candidates[key] = candidate
            ManualUiCandidate(
                key = key,
                source = candidate.source.displayName,
                title = candidate.title,
                artists = candidate.artists.joinToString(" / "),
                album = candidate.album,
                duration = candidate.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1_000 % 60) }.orEmpty(),
                quality = when (candidate.qualityHint) {
                    LyricsQuality.WORD_SYNCED -> "逐字"
                    LyricsQuality.LINE_SYNCED -> "逐行"
                    LyricsQuality.PLAIN -> "普通"
                    null -> "未知"
                },
                score = row.score,
            )
        }
        return ManualUiResponse(true, "找到 ${mapped.size} 个候选。", candidates = mapped)
    }

    private fun previewResponse(candidate: LyricsCandidate): ManualUiResponse {
        val resolved = preview(candidate) ?: return ManualUiResponse(false, "该候选没有可用歌词。")
        return ManualUiResponse(
            ok = true,
            preview = resolved.document.lines.take(120).map { line ->
                ManualUiPreviewLine(
                    main = line.text,
                    secondary = line.translation?.takeIf(String::isNotBlank)
                        ?: line.romanization?.takeIf(String::isNotBlank),
                )
            },
        )
    }

    private fun candidate(request: ManualUiRequest): LyricsCandidate? = request.candidateKey?.let(candidates::get)

    private fun sources() = listOf(ManualUiSource(null, "全部在线来源")) +
        LyricsSource.entries.filter { it != LyricsSource.LOCAL }.map { ManualUiSource(it.name, it.displayName) }
}
