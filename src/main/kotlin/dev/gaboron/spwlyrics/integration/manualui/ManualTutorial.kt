package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.application.ResolvedLyrics
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialSong
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialStep
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialTarget

internal enum class ManualTutorialTarget : TutorialTarget { TRACK, SEARCH, SOURCE, STATUS, RESULTS, PREVIEW, METADATA, DELAY, APPLY, RECOVERY, IMPORT }

internal enum class ManualTutorialStep(
    override val title: String,
    override val detail: String,
    override val target: ManualTutorialTarget,
    val preview: Boolean = false,
) : TutorialStep {
    TRACK("先确认当前歌曲", "这里显示正在处理的歌曲。切歌后点“刷新歌曲”或按 F5，再确认歌名与歌手。\n\n教程展示的是虚拟歌曲《星光来信》，不会搜索网络、应用歌词或改变播放。结束后回到真实搜索页。", ManualTutorialTarget.TRACK),
    SEARCH("输入关键词再搜索", "点击搜索框，填写歌名、歌手或专辑，再点“搜索”或按 Enter。结果太少时可缩短关键词；Ctrl+F 可快速聚焦搜索框。", ManualTutorialTarget.SEARCH),
    SOURCE("选择搜索来源", "点击来源菜单，可搜索全部来源或只查一个来源。默认的“全部来源”遵循插件配置里的来源列表；MusicBrainz 只提供元数据。\n\n切换来源后点击“搜索”取得新结果。", ManualTutorialTarget.SOURCE),
    STATUS("查看来源状态", "这里显示本次检索各来源的状态。鼠标悬停可查看具体原因。个别来源失败或超时不会阻止其他来源返回结果；可再次搜索，或换一个来源。示例中的超时只是演示。", ManualTutorialTarget.STATUS),
    RESULTS("对照候选并选择", "先核对歌名、歌手、专辑、时长与录音版本，再点击候选预览。右上角“排序”可按需要调整顺序；方向键也能切换候选。\n\n“当前应用”“自动搜索”会置顶，它们可能与正在预览的候选不同。点击候选只改变预览。", ManualTutorialTarget.RESULTS),
    PREVIEW("先听歌曲，再核对预览", "这里显示所选候选的正文、翻译与音译，用来核对内容和版本。窄窗口点击“搜索结果 / 歌词预览”切换；读取失败时可再次点击候选重试。", ManualTutorialTarget.PREVIEW, true),
    METADATA("展开查看候选元数据", "点击“元数据查看”展开歌曲标签与歌词元数据，可选中文字复制。教程已替你展开示例。\n\n这里用于查看；要把在线信息写进文件，请使用底部“应用元数据”。", ManualTutorialTarget.METADATA, true),
    DELAY("歌词早了或晚了怎么办", "点击“时间调整”展开。输入整数毫秒或用 −100 / +100 微调，然后点“保存”；“重置”恢复为 0。\n\n正数延后、负数提前，例如歌词早了 500 毫秒就填 500。范围为 −60000 到 60000，只影响当前歌曲的受支持歌词。", ManualTutorialTarget.DELAY, true),
    APPLY("确认后应用歌词", "核对预览后点“应用歌词”，或按 Ctrl+Enter。此操作保存当前歌曲的手动选择，不修改歌曲文件；按钮变为“已应用”表示该候选已经在使用。", ManualTutorialTarget.APPLY, true),
    RECOVERY("选错歌词时恢复", "想让插件重新决定，点“恢复自动匹配”；想交回宿主或音源原来的歌词，点旁边的“切回本地歌词”。\n\n两者都会取消当前的在线手动选择。切歌后请先刷新歌曲，再操作。", ManualTutorialTarget.RECOVERY),
    IMPORT("需要写入歌曲信息时", "选中候选后点“应用元数据”，到编辑页对照并勾选标签、封面和歌词，应用到草稿后再确认写入。导入歌词无需先应用为播放歌词。\n\n教程结束后即可使用真实页面；配置中的“重置全部教程”可让三页教程都重新出现。", ManualTutorialTarget.IMPORT),
}

/** All candidates, source statuses and lyric selections are isolated from the real search session. */
internal class ManualTutorialDemo : AutoCloseable {
    private val song = TutorialSong
    private val candidates = listOf(song.candidate, song.candidate.copy(source = LyricsSource.QQ, remoteId = "tutorial-qq"),
        song.candidate.copy(source = LyricsSource.NETEASE, remoteId = "tutorial-live", title = "星光来信（Live）"))
    private val rows = candidates.map { MatchEngine.score(song.query, it) }
    private val initial = ManualSearchState(query = song.query, keywords = "星光来信 示例歌手", candidates = rows,
        automatic = rows[0], applied = rows[0], selected = rows[1], document = song.document,
        lyricsMetadata = mapOf("title" to listOf("星光来信"), "artist" to listOf("示例歌手")),
        delayText = "500", savedDelay = 0, readingTrack = false, searched = true,
        sourceStatuses = listOf(LyricsSource.AMLL, LyricsSource.QQ, LyricsSource.NETEASE).associateWith {
            ProviderSearchStatus(it, if (it == LyricsSource.NETEASE) ProviderSearchPhase.TIMED_OUT else ProviderSearchPhase.AVAILABLE,
                "教程示例状态，未请求在线服务。")
        }, status = "教程虚拟歌曲与候选 · 仅供演示。")
    val controller = ManualSearchController(object : ManualSearchOperations {
        override fun currentQuery() = song.query
        override fun applied(query: TrackQuery) = song.candidate
        override fun search(query: TrackQuery, keywords: String, source: LyricsSource?, onStatus: (ProviderSearchStatus) -> Unit) = rows
        override fun preview(candidate: LyricsCandidate): ResolvedLyrics? = null
        override fun automatic(query: TrackQuery): ResolvedLyrics? = null
        override fun inspect(candidates: List<LyricsCandidate>, report: (LyricsCandidate) -> Unit) = Unit
        override fun apply(query: TrackQuery, candidate: LyricsCandidate) = false
        override fun useLocal(query: TrackQuery) = false
        override fun useAutomatic(query: TrackQuery) = false
        override fun delay(query: TrackQuery) = 0
        override fun setDelay(query: TrackQuery, delayMs: Int) = false
        override fun metadata(candidate: LyricsCandidate) = candidate
        override fun editMetadata(query: TrackQuery, candidate: LyricsCandidate, document: LyricsDocument?, preserveDraft: Boolean) = Unit
    }, initial)

    override fun close() = controller.close()
}
