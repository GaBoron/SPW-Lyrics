package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gaboron.spwlyrics.codec.MetadataLyricsImport
import dev.gaboron.spwlyrics.codec.MetadataLyricsExport
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.codec.TtmlEditCodec
import dev.gaboron.spwlyrics.codec.TtmlLyricRows
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.storage.AudioMetadataSnapshot
import dev.gaboron.spwlyrics.storage.AudioMetadataStore
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal interface MetadataEditorOperations {
    fun currentAudioPath(): String?
    fun chooseAudioFile(): Path?
    fun chooseLyricsFile(): Path?
    fun afterSave(path: Path)
    fun openSearch()
}

/** All state transitions occur on the event thread; file IO and parsing run on dedicated workers. */
internal class MetadataEditorController(
    private val store: AudioMetadataStore,
    private val operations: MetadataEditorOperations,
) : AutoCloseable {
    var state by mutableStateOf(MetadataEditorState())
        private set
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "spw-lyrics-metadata").apply { isDaemon = true } }
    private val parser = Executors.newSingleThreadScheduledExecutor { Thread(it, "spw-lyrics-metadata-preview").apply { isDaemon = true } }
    private var previewTask: ScheduledFuture<*>? = null
    private var previewGeneration = 0
    @Volatile private var closed = false

    fun current() = requestDiscard {
        run({ operations.currentAudioPath()?.takeIf(String::isNotBlank)?.let { store.read(Path.of(it)) }
            ?: error("当前没有本地歌曲，请先播放歌曲或点击“打开歌曲”。") }, ::loaded)
    }

    fun openFile() = requestDiscard {
        operations.chooseAudioFile()?.let { path -> run({ store.read(path) }, ::loaded) }
    }

    fun reload() = requestDiscard {
        state.loaded?.path?.let { path -> run({ store.read(path) }, ::loaded) }
    }

    fun field(field: AudioMetadataField, value: String) {
        if (closed || state.busy || state.loaded == null) return
        state = state.copy(draft = state.draft.copy(fields = state.draft.fields + (field to value)), error = false)
    }

    fun lyrics(text: String) {
        if (closed || state.busy || state.loaded == null) return
        state = state.copy(draft = state.draft.copy(lyrics = text), sourceDocument = null,
            structured = null, structuredError = null, lineEdits = emptyMap(), error = false)
        preview()
    }

    fun selectLine(id: Int, ctrl: Boolean = false, shift: Boolean = false) {
        if (!state.busy) state = MetadataLyricSelection.select(state, id, ctrl, shift)
    }

    fun selectAllLines() {
        if (!state.busy) state = state.copy(selectedLineIds = state.structured?.lines?.map { it.id }?.toSet().orEmpty(),
            selectedLineId = state.selectedLineId ?: state.structured?.lines?.firstOrNull()?.id)
    }

    fun editLine(change: (EditableLyricLine) -> EditableLyricLine) {
        editLines(setOfNotNull(state.selectedLineId), change)
    }

    fun editSelected(change: (EditableLyricLine) -> EditableLyricLine) = editLines(state.selectedLineIds, change)

    private fun editLines(ids: Set<Int>, change: (EditableLyricLine) -> EditableLyricLine) {
        if (closed || state.busy || state.loaded == null) return
        val base = state.structured ?: return
        if (ids.isEmpty()) return
        val changes = runCatching { ids.associateWith { change(state.line(it) ?: error("歌词行已失效。")) } }
        val updated = changes.getOrElse { state = state.copy(previewError = it.message); return }
        val edits = (state.lineEdits + updated).filter { (id, line) -> line != base.lines.first { it.id == id } }
        previewTask?.cancel(false)
        val generation = ++previewGeneration
        state = state.copy(lineEdits = edits, validating = true, previewError = null, error = false)
        previewTask = parser.schedule({
            val result = runCatching {
                val raw = if (edits.isEmpty()) base.raw else TtmlEditCodec.apply(base, edits)
                raw to runCatching { MetadataLyricsImport.preview(raw) }
            }
            EventQueue.invokeLater {
                if (!closed && generation == previewGeneration) result.onSuccess { (raw, parsed) ->
                    state = state.copy(draft = state.draft.copy(lyrics = raw), sourceDocument = parsed.getOrNull(), preview = parsed.getOrNull(),
                        previewError = parsed.exceptionOrNull()?.message,
                        validating = false, format = MetadataLyricsFormat.TTML,
                        status = "歌词修改已记入草稿。逐句编辑使用 TTML 保存声部、背景和逐字信息；可再转换为其他格式。")
                }.onFailure { state = state.copy(validating = false, previewError = it.message ?: "歌词编辑内容无效。") }
            }
        }, 200, TimeUnit.MILLISECONDS)
    }

    fun shiftLine(deltaMs: Long) {
        editLine { shift(it, deltaMs) }
    }

    fun shiftSelected(delta: String) {
        val milliseconds = delta.trim().toLongOrNull()
        if (milliseconds == null) { state = state.copy(previewError = "批量延迟请填写整数毫秒，负数表示提前。"); return }
        editSelected { shift(it, milliseconds) }
    }

    private fun shift(line: EditableLyricLine, deltaMs: Long) = line.copy(
            start = LyricEditTime.shift(line.start, deltaMs), end = LyricEditTime.shift(line.end, deltaMs),
            words = line.words.map { it.copy(start = LyricEditTime.shift(it.start, deltaMs), end = LyricEditTime.shift(it.end, deltaMs)) },
        )

    fun addLine() = changeRows { snapshot ->
        TtmlLyricRows.add(snapshot.structured, snapshot.lineEdits, snapshot.selectedLineId)
    }

    fun deleteSelected() = changeRows { snapshot ->
        val base = snapshot.structured ?: return@changeRows null
        if (snapshot.selectedLineIds.isEmpty()) return@changeRows null
        val result = TtmlLyricRows.delete(base, snapshot.lineEdits, snapshot.selectedLineIds)
        result to result.lines.getOrNull((snapshot.selectedLineId ?: 0).coerceAtMost(result.lines.lastIndex).coerceAtLeast(0))?.id
    }

    private fun changeRows(change: (MetadataEditorState) -> Pair<EditableLyrics, Int?>?) {
        if (closed || !state.canEditRows) return
        val snapshot = state
        previewTask?.cancel(false)
        val generation = ++previewGeneration
        state = state.copy(validating = true)
        previewTask = parser.schedule({
            val result = runCatching { change(snapshot)?.let { (base, id) ->
                Triple(base, id, runCatching { MetadataLyricsImport.preview(base.raw) })
            } }
            EventQueue.invokeLater {
                if (!closed && generation == previewGeneration) {
                    state = state.copy(validating = false)
                    result.onSuccess { edited ->
                        if (edited == null) return@onSuccess
                        val (base, id, parsed) = edited
                        state = state.copy(structured = base, lineEdits = emptyMap(),
                            draft = state.draft.copy(lyrics = base.raw), format = MetadataLyricsFormat.TTML,
                            preview = parsed.getOrNull(), sourceDocument = parsed.getOrNull(), previewError = parsed.exceptionOrNull()?.message,
                            selectedLineId = id, selectedLineIds = setOfNotNull(id), selectionAnchor = id,
                            status = "歌词行修改已记入草稿，点击“写入歌曲文件”保存。")
                    }.onFailure { state = state.copy(previewError = it.message) }
                }
            }
        }, 0, TimeUnit.MILLISECONDS)
    }

    fun format(format: MetadataLyricsFormat) {
        if (!state.busy) state = state.copy(format = format)
    }

    fun convert() {
        if (closed || state.busy || state.loaded == null || state.validating) return
        val document = state.sourceDocument ?: state.preview ?: return
        val format = state.format
        run({ MetadataLyricsExport.encode(document, format) }, { text ->
            lyrics(text)
            state = state.copy(sourceDocument = document, status = "已转换为 ${format.label}，点击“写入歌曲文件”保存。")
        })
    }

    fun importSearch(query: TrackQuery, candidate: dev.gaboron.spwlyrics.domain.LyricsCandidate, document: LyricsDocument?) = requestDiscard {
        val format = state.format
        run({ store.read(Path.of(query.path)) to document?.let { MetadataLyricsExport.encode(it, format) } }, { (snapshot, text) ->
            loaded(snapshot)
            if (text != null) lyrics(text)
            state = state.copy(format = format, sourceDocument = document, onlineImport = dev.gaboron.spwlyrics.domain.OnlineMetadataImport.from(candidate),
                status = "已读取在线信息。请在左侧勾选字段并应用到草稿；核对后点击“写入歌曲文件”。")
        })
    }

    fun selectImport(field: AudioMetadataField, selected: Boolean) {
        val pending = state.onlineImport ?: return
        if (!state.busy) state = state.copy(onlineImport = pending.copy(selected = if (selected) pending.selected + field else pending.selected - field))
    }

    fun importMode(fillMissing: Boolean) {
        if (!state.busy) state = state.copy(onlineImport = state.onlineImport?.copy(fillMissing = fillMissing))
    }

    fun applyImport() {
        val pending = state.onlineImport ?: return
        if (state.busy || state.loaded == null) return
        state = state.copy(draft = pending.apply(state.draft), error = false,
            status = "所选字段已应用到草稿。请核对后点击“写入歌曲文件”。")
    }

    fun openSearch() {
        if (!closed && !state.busy) operations.openSearch()
    }

    fun importLyrics() {
        if (closed || state.busy || state.loaded == null) return
        val path = operations.chooseLyricsFile() ?: return
        run({ MetadataLyricsImport.read(path) }, { text ->
            lyrics(text)
            state = state.copy(status = "已导入 ${path.fileName}，点击“写入歌曲文件”保存。")
        })
    }

    fun save() {
        if (closed || !state.canSave) return
        val original = state.loaded ?: return
        val edited = state.draft
        run({
            if (edited.lyrics.isNotBlank() && edited.lyrics != original.metadata.lyrics) MetadataLyricsImport.preview(edited.lyrics)
            store.save(original, edited)
        }, { saved ->
            loaded(saved.snapshot)
            val refresh = runCatching { operations.afterSave(saved.snapshot.path) }.isSuccess
            state = state.copy(status = "已写入。" +
                (if (refresh) "歌曲信息可重新扫描曲库后更新。" else "请切歌后重试歌词显示，并重新扫描曲库更新歌曲信息。") + saved.notice)
        })
    }

    fun requestDiscard(action: () -> Unit) {
        if (closed || state.busy) return
        if (state.dirty) state = state.copy(pendingDiscard = action)
        else action()
    }

    fun discard() {
        val action = state.pendingDiscard ?: return
        state = state.copy(pendingDiscard = null)
        action()
    }

    fun cancelDiscard() { state = state.copy(pendingDiscard = null) }

    private fun loaded(snapshot: AudioMetadataSnapshot) {
        state = MetadataEditorState(loaded = snapshot, draft = snapshot.metadata, format = state.format,
            status = "已读取歌曲标签。修改后点击“写入歌曲文件”；多值字段每行填写一个值。")
        preview()
    }

    private fun preview() {
        previewTask?.cancel(false)
        val generation = ++previewGeneration
        val text = state.draft.lyrics
        state = state.copy(validating = true, previewError = null)
        previewTask = parser.schedule({
            val result = runCatching { MetadataLyricsImport.preview(text) }
            val structured = runCatching { result.getOrNull()?.let(TtmlEditCodec::open) }
            EventQueue.invokeLater {
                if (!closed && generation == previewGeneration) state = state.copy(
                    validating = false, preview = result.getOrNull(),
                    structured = structured.getOrNull(), structuredError = structured.exceptionOrNull()?.message,
                    selectedLineId = structured.getOrNull()?.lines?.firstOrNull()?.id,
                    selectedLineIds = setOfNotNull(structured.getOrNull()?.lines?.firstOrNull()?.id),
                    selectionAnchor = structured.getOrNull()?.lines?.firstOrNull()?.id,
                    previewError = result.exceptionOrNull()?.let { "歌词格式无法解析：${it.message ?: "请检查歌词内容"}" },
                )
            }
        }, 300, TimeUnit.MILLISECONDS)
    }

    private fun <T> run(task: () -> T, success: (T) -> Unit) {
        if (closed || state.busy) return
        state = state.copy(busy = true, error = false, status = "正在处理歌曲文件…")
        worker.execute {
            val result = runCatching(task)
            EventQueue.invokeLater {
                if (!closed) {
                    state = state.copy(busy = false)
                    result.onSuccess(success).onFailure {
                        state = state.copy(error = true, status = it.message ?: "操作失败，请检查文件权限后重试。")
                    }
                }
            }
        }
    }

    override fun close() {
        closed = true
        previewTask?.cancel(true)
        parser.shutdownNow()
        // An in-flight verified file write is allowed to finish when the plugin closes.
        worker.shutdown()
    }
}
