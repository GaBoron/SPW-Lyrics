package dev.gaboron.spwlyrics.integration.manualui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.MatchEngine
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import dev.gaboron.spwlyrics.domain.ProviderSearchPhase
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.codec.TtmlVocalParts
import dev.gaboron.spwlyrics.codec.LyricsMetadataReader
import dev.gaboron.spwlyrics.storage.LyricsDelaySettings
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Owns asynchronous requests and presentation state; domain rules stay behind operations. */
internal class ManualSearchController(private val operations: ManualSearchOperations) : AutoCloseable {
    var state by mutableStateOf(ManualSearchState())
        private set
    private val workers = Executors.newFixedThreadPool(3) { task ->
        Thread(task, "spw-lyrics-manual-search").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    private var trackGeneration = 0
    private var searchGeneration = 0
    private var previewGeneration = 0
    private var automaticGeneration = 0
    private var inspection: Future<*>? = null
    private var automatic: Future<*>? = null

    fun searchMetadata(query: TrackQuery) {
        if (closed || state.mutating) return
        inspection?.cancel(true)
        automatic?.cancel(true)
        automaticGeneration++
        trackGeneration++
        searchGeneration++
        previewGeneration++
        state = ManualSearchState(query = query, metadataOnly = true, readingTrack = false,
            keywords = listOf(query.title, query.artists.joinToString(" ")).filter(String::isNotBlank).joinToString(" "),
            status = "正在为编辑中的歌曲匹配在线信息与封面…")
        search()
    }

    fun refreshTrack() {
        if (closed || state.mutating || state.readingTrack && trackGeneration > 0) return
        inspection?.cancel(true)
        automatic?.cancel(true)
        automaticGeneration++
        val generation = ++trackGeneration
        searchGeneration++
        previewGeneration++
        state = ManualSearchState(source = state.source)
        submit({ trackGeneration == generation }, {
            operations.currentQuery()?.let { it to operations.delay(it) }
        }, { loaded ->
            state = state.copy(
                query = loaded?.first, keywords = loaded?.first?.title.orEmpty(),
                delayText = (loaded?.second ?: 0).toString(), savedDelay = loaded?.second ?: 0,
                readingTrack = false, status = if (loaded == null) "请先播放歌曲，再点击“刷新歌曲”。" else "正在搜索…",
            )
            if (loaded != null) search()
        }, { state = state.copy(readingTrack = false); reportFailure(it) })
    }

    fun keywords(value: String) {
        if (value != state.keywords) { invalidateSearch(); state = state.copy(keywords = value) }
    }
    fun source(value: LyricsSource?) {
        if (value != state.source) { invalidateSearch(); state = state.copy(source = value) }
    }

    private fun invalidateSearch() {
        inspection?.cancel(true)
        searchGeneration++
        previewGeneration++
        state = state.copy(candidates = emptyList(), selected = null, document = null, lyricsMetadata = emptyMap(),
            searching = false, previewing = false, searched = false, sourceStatuses = ProviderSearchStatus.unchecked(),
            status = "搜索条件已更改，请点击搜索。", error = false)
    }
    fun delayText(value: String) {
        if (value.length <= 7 && value.matches(Regex("-?\\d*"))) state = state.copy(delayText = value)
    }

    fun stepDelay(delta: Int) {
        val value = state.delayValue ?: return invalidDelay()
        state = state.copy(delayText = (value + delta).coerceIn(LyricsDelaySettings.MIN_MS, LyricsDelaySettings.MAX_MS).toString())
    }

    fun search() {
        if (!state.canSearch) return
        val query = state.query ?: return
        val keywords = state.keywords.trim()
        if (keywords.isEmpty()) { status("请输入搜索关键词。", true); return }
        val source = state.source
        val generation = ++searchGeneration
        var refreshAfterSearch = false
        inspection?.cancel(true)
        previewGeneration++
        state = state.copy(candidates = emptyList(), selected = null, document = null, lyricsMetadata = emptyMap(),
            sourceStatuses = ProviderSearchStatus.unchecked(),
            searching = true, previewing = false, searched = false, status = "正在搜索…", error = false)
        refreshAutomatic(query)
        submit({ searchGeneration == generation }, { operations.search(query, keywords, source) { update ->
            EventQueue.invokeLater {
                if (!closed && searchGeneration == generation) {
                    state = state.copy(sourceStatuses = state.sourceStatuses + (update.source to update))
                    if (update.resultsChanged && state.selected == null && !state.mutating) {
                        if (state.searching) refreshAfterSearch = true else search()
                    }
                }
            }
        } }, { rows ->
            state = state.copy(candidates = rows, searching = false, searched = true,
                status = "")
            if (refreshAfterSearch) search() else inspect(rows, generation)
        }, { error ->
            state = state.copy(searching = false, searched = true, sourceStatuses = state.sourceStatuses.mapValues { (_, status) ->
                if (status.phase == ProviderSearchPhase.SEARCHING) status.copy(phase = ProviderSearchPhase.FAILED, detail = "搜索未完成，请重试。") else status
            })
            reportFailure(error)
        })
    }

    private fun refreshAutomatic(query: TrackQuery) {
        automatic?.cancel(true)
        val generation = ++automaticGeneration
        state = state.copy(automatic = null, automaticDocument = null, matchingAutomatic = true)
        automatic = submit({ automaticGeneration == generation }, { operations.automatic(query) }, { resolved ->
            val row = resolved?.let { MatchEngine.score(query, it.candidate.copy(qualityHint = it.document.quality)) }
            state = state.copy(automatic = row, automaticDocument = resolved?.document, matchingAutomatic = false)
        }, { state = state.copy(matchingAutomatic = false) })
    }

    private fun inspect(rows: List<CandidateScore>, generation: Int) {
        inspection = workers.submit {
            runCatching {
                operations.inspect(rows.map { it.candidate }) { candidate ->
                    EventQueue.invokeLater {
                        if (!closed && searchGeneration == generation) {
                            fun updated(row: CandidateScore) = if (sameCandidate(row.candidate, candidate))
                                row.copy(candidate = row.candidate.copy(qualityHint = candidate.qualityHint)) else row
                            state = state.copy(candidates = state.candidates.map(::updated), selected = state.selected?.let(::updated))
                        }
                    }
                }
            }
        }
    }

    fun select(row: CandidateScore) {
        if (closed || state.mutating || (!state.isAutomatic(row) && state.candidates.none { sameCandidate(it.candidate, row.candidate) }) ||
            state.selected?.candidate?.let { sameCandidate(it, row.candidate) } == true && (state.previewing || state.document != null)) return
        val generation = ++previewGeneration
        val automaticDocument = state.automaticDocument.takeIf { state.isAutomatic(row) }
        state = state.copy(selected = row, document = null, lyricsMetadata = emptyMap(), previewing = true)
        submit({ previewGeneration == generation }, {
            (automaticDocument ?: operations.preview(row.candidate)?.document)
                ?.let { Triple(it, TtmlVocalParts.mainAgent(it), LyricsMetadataReader.read(it)) }
        }, { resolved ->
            val inspected = row.copy(candidate = row.candidate.copy(qualityHint = resolved?.first?.quality))
            state = state.copy(selected = inspected, candidates = state.candidates.map { if (sameCandidate(it.candidate, row.candidate)) inspected else it },
                document = resolved?.first, lyricsMetadata = resolved?.third.orEmpty(),
                previewMainAgent = resolved?.second ?: "v1", previewing = false,
                status = if (resolved == null && row.candidate.source.hasLyrics) "歌词读取失败，点击此项可重试，或选择其他候选。" else "",
                error = resolved == null && row.candidate.source.hasLyrics)
        }, { state = state.copy(previewing = false); reportFailure(it) })
    }

    fun apply() {
        val query = state.query ?: return
        val candidate = state.selected?.candidate ?: return
        if (!candidate.source.hasLyrics || state.document == null || state.busy) return
        mutate("歌词已应用并保存为当前歌曲的手动选择。", action = { operations.apply(query, candidate) })
    }

    fun useLocal() {
        val query = state.query ?: return
        mutate("已切回 SPW 本地歌词流程。", action = { operations.useLocal(query) })
    }

    fun useAutomatic() {
        val query = state.query ?: return
        mutate("已清除手动选择，正在重新自动匹配。", action = { operations.useAutomatic(query) })
    }

    fun saveDelay(reset: Boolean = false) {
        if (!state.canMutate) return
        val query = state.query ?: return
        val value = if (reset) 0 else state.delayValue ?: return invalidDelay()
        if (reset) state = state.copy(delayText = "0")
        mutate("已保存本曲延迟：$value ms；未刷新时可切歌后再切回。", {
            operations.setDelay(query, value)
        }) { state = state.copy(savedDelay = value) }
    }

    fun editMetadata() {
        if (state.busy) return
        val query = state.query ?: return
        val candidate = state.selected?.candidate ?: return
        val document = state.document
        val preserveDraft = state.metadataOnly
        val generation = searchGeneration
        state = state.copy(mutating = true, error = false, status = "正在获取在线元数据…")
        submit({ searchGeneration == generation }, { operations.metadata(candidate) }, { enriched ->
            state = state.copy(mutating = false, status = "在线元数据已读取。")
            operations.editMetadata(query, enriched, document, preserveDraft)
        }, { state = state.copy(mutating = false); reportFailure(it) })
    }

    private fun mutate(message: String, action: () -> Boolean, success: () -> Unit = {}) {
        if (!state.canMutate) return
        val generation = trackGeneration
        state = state.copy(mutating = true, error = false, status = "正在处理…")
        submit({ trackGeneration == generation }, action, { result ->
            state = state.copy(mutating = false)
            if (result) { success(); status(message) }
            else status("操作未完成：当前歌曲可能已变化，请刷新当前歌曲后重试。", true)
        }, { state = state.copy(mutating = false); reportFailure(it) })
    }

    private fun status(message: String, error: Boolean = false) { state = state.copy(status = message, error = error) }
    private fun invalidDelay() = status("请输入 -60000 到 60000 之间的整数毫秒。", true)
    private fun reportFailure(error: Throwable) = status("操作失败：${error.message ?: "未知错误"}", true)

    private fun <T> submit(current: () -> Boolean, action: () -> T, complete: (T) -> Unit, failure: (Throwable) -> Unit): Future<*>? {
        if (closed) return null
        return runCatching {
            workers.submit {
                val result = runCatching(action)
                EventQueue.invokeLater {
                    if (!closed && current()) result.onSuccess(complete).onFailure(failure)
                }
            }
        }.getOrElse {
            if (!closed && current()) failure(it)
            null
        }
    }

    // A submitted apply/save may finish after the user closes the view, as before.
    override fun close() { closed = true; inspection?.cancel(true); automatic?.cancel(true); workers.shutdown() }
}
