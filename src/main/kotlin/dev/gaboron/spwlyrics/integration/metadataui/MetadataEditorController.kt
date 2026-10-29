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
import dev.gaboron.spwlyrics.codec.AudioArtworkImages
import dev.gaboron.spwlyrics.application.OnlineArtworkImport
import dev.gaboron.spwlyrics.codec.TimingWordPreparation
import dev.gaboron.spwlyrics.domain.TimingSplit
import dev.gaboron.spwlyrics.domain.EditableLyricLine
import dev.gaboron.spwlyrics.domain.EditableLyrics
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.storage.AudioMetadataSnapshot
import dev.gaboron.spwlyrics.storage.AudioMetadataSaved
import dev.gaboron.spwlyrics.storage.AudioMetadataStore
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal interface MetadataEditorOperations {
    fun lyricsFormat(): MetadataLyricsFormat = MetadataLyricsFormat.TTML
    fun saveLyricsFormat(format: MetadataLyricsFormat): Boolean = true
    fun currentAudioPath(): String?
    fun chooseAudioFile(): Path?
    fun chooseLyricsFile(): Path?
    fun chooseCoverFile(): Path?
    fun afterSave(path: Path)
    fun openSearch(query: TrackQuery)
}

/** All state transitions occur on the event thread; file IO and parsing run on dedicated workers. */
internal class MetadataEditorController(
    private val store: AudioMetadataStore,
    private val operations: MetadataEditorOperations,
    private val artworkImport: OnlineArtworkImport = OnlineArtworkImport(),
    private val lyricsWritingEnabled: Boolean = true,
) : AutoCloseable {
    var state by mutableStateOf(MetadataEditorState(format = operations.lyricsFormat(), lyricsWritingEnabled = lyricsWritingEnabled))
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

    fun openPath(path: Path) = requestDiscard { run({ store.read(path) }, ::loaded) }

    fun clear() {
        if (closed || state.busy) return
        previewTask?.cancel(false)
        previewGeneration++
        state = MetadataEditorState(format = operations.lyricsFormat(), lyricsWritingEnabled = lyricsWritingEnabled)
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
        state = state.copy(draft = state.draft.copy(lyrics = text),
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
        if (state.selectedLineIds.size != 1) return
        editLines(setOfNotNull(state.selectedLineId), change)
    }

    fun editSelected(change: (EditableLyricLine) -> EditableLyricLine) = editLines(state.selectedLineIds, change)

    private fun editLines(ids: Set<Int>, change: (EditableLyricLine) -> EditableLyricLine) {
        if (closed || state.busy || state.loaded == null) return
        val base = state.structured ?: return
        if (ids.isEmpty()) return
        val changes = runCatching { ids.associateWith { change(state.line(it) ?: error("歌词行已失效。")) } }
        val updated = changes.getOrElse { state = state.copy(previewError = it.message); return }
        stageLineEdits(updated)
    }

    fun applyTimingLines(expectedRaw: String, updated: Map<Int, EditableLyricLine>): Boolean {
        val base = state.structured ?: return false
        if (closed || state.busy || state.loaded == null || base.raw != expectedRaw) return false
        if (updated.any { (id, line) -> base.lines.firstOrNull { it.id == id }?.path != line.path }) return false
        stageLineEdits(updated)
        return true
    }

    fun prepareTimingWords(mode: TimingSplit) {
        val base = state.structured ?: return
        if (closed || state.busy || state.validating || state.loaded == null) return
        val edits = state.lineEdits
        run({ TimingWordPreparation.prepare(TtmlEditCodec.openRaw(TtmlEditCodec.apply(base, edits)), mode) }, { result ->
            if (result.prepared > 0) {
                lyrics(result.raw)
                state = state.copy(structured = TtmlEditCodec.openRaw(result.raw))
            }
            state = state.copy(status = "已为 ${result.prepared} 行准备逐字内容；${result.skipped} 行含扩展标记，保留原样。已有逐字内容未重新分词。")
        })
    }

    private fun stageLineEdits(updated: Map<Int, EditableLyricLine>) {
        val base = state.structured ?: return
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
                    state = state.copy(draft = state.draft.copy(lyrics = raw), preview = parsed.getOrNull(),
                        previewError = parsed.exceptionOrNull()?.message,
                        validating = false,
                        status = "歌词修改已记入草稿，保留声部、背景和逐字信息；写入时使用所选格式。")
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

    fun moveLine(id: Int, targetId: Int, after: Boolean) = changeRows { snapshot ->
        val base = snapshot.structured ?: return@changeRows null
        if (id == targetId) return@changeRows null
        TtmlLyricRows.move(base, snapshot.lineEdits, id, targetId, after) to id
    }

    fun deleteSelected() = changeRows { snapshot ->
        val base = snapshot.structured ?: return@changeRows null
        if (snapshot.selectedLineIds.isEmpty()) return@changeRows null
        val index = base.lines.indexOfFirst { it.id == snapshot.selectedLineId }.coerceAtLeast(0)
        val result = TtmlLyricRows.delete(base, snapshot.lineEdits, snapshot.selectedLineIds)
        result to result.lines.getOrNull(index.coerceAtMost(result.lines.lastIndex).coerceAtLeast(0))?.id
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
                            draft = state.draft.copy(lyrics = base.raw),
                            preview = parsed.getOrNull(), previewError = parsed.exceptionOrNull()?.message,
                            selectedLineId = id, selectedLineIds = setOfNotNull(id), selectionAnchor = id,
                            status = "歌词行修改已记入草稿，点击“写入歌曲文件”保存。")
                    }.onFailure { state = state.copy(previewError = it.message) }
                }
            }
        }, 0, TimeUnit.MILLISECONDS)
    }

    fun format(format: MetadataLyricsFormat) {
        if (closed || state.busy) return
        val pending = state.pendingWrite?.let { request ->
            request.copy(format = format, losses = request.document?.let { MetadataLyricsExport.losses(it, format) }.orEmpty())
        }
        state = state.copy(format = format, pendingWrite = pending, error = false,
            status = "写入时将使用 ${format.label}，草稿内容已保留。")
        runCatching { check(operations.saveLyricsFormat(format)) { "已选择 ${format.label}，但未能记住本次选择，请检查配置文件权限。" } }
            .onFailure { state = state.copy(error = true, status = it.message.orEmpty()) }
    }

    fun importSearch(query: TrackQuery, candidate: dev.gaboron.spwlyrics.domain.LyricsCandidate, document: LyricsDocument?, preserveDraft: Boolean = false) {
        if (closed || state.busy) return
        val sameFile = state.loaded?.path?.let { path ->
            runCatching { java.nio.file.Files.isSameFile(path, Path.of(query.path)) }.getOrDefault(false)
        } == true
        if (preserveDraft && sameFile) {
            run({ prepareOnlineImport(candidate, document) }, { pending ->
                state = state.copy(onlineImport = pending, status = "已读取在线信息、封面与可用歌词，原草稿已保留。请勾选并应用。")
            })
        } else requestDiscard { loadSearch(query, candidate, document) }
    }

    private fun loadSearch(query: TrackQuery, candidate: dev.gaboron.spwlyrics.domain.LyricsCandidate, document: LyricsDocument?) {
        run({ store.read(Path.of(query.path)) to prepareOnlineImport(candidate, document) }, { (snapshot, pending) ->
            loaded(snapshot)
            state = state.copy(onlineImport = pending,
                status = "已读取在线信息。请勾选字段、封面或歌词并应用到草稿；核对后写入歌曲文件。")
        })
    }

    private fun prepareOnlineImport(candidate: dev.gaboron.spwlyrics.domain.LyricsCandidate, document: LyricsDocument?) =
        artworkImport.prepare(candidate).copy(lyricsText = document?.let { MetadataLyricsExport.encode(it, MetadataLyricsFormat.TTML) }, lyricsDocument = document)

    fun selectImport(field: AudioMetadataField, selected: Boolean) {
        val pending = state.onlineImport ?: return
        if (!state.busy) state = state.copy(onlineImport = pending.copy(selected = if (selected) pending.selected + field else pending.selected - field))
    }

    fun importMode(fillMissing: Boolean) {
        if (!state.busy) state = state.copy(onlineImport = state.onlineImport?.copy(fillMissing = fillMissing))
    }

    fun selectCoverImport() {
        val pending = state.onlineImport ?: return
        if (!closed && !state.busy) state = state.copy(onlineImport = pending.copy(coverSelected = !pending.coverSelected))
    }

    fun selectLyricsImport() {
        val pending = state.onlineImport ?: return
        if (!closed && !state.busy) state = state.copy(onlineImport = pending.copy(lyricsSelected = !pending.lyricsSelected))
    }

    fun retryCoverImport() {
        val pending = state.onlineImport ?: return
        run({ artworkImport.retry(pending) }, { state = state.copy(onlineImport = it, status = "在线封面已重新读取，请核对后应用。") })
    }

    fun importCover() {
        if (closed || state.busy || state.loaded == null) return
        val path = operations.chooseCoverFile() ?: return
        run({ AudioArtworkImages.read(path) }, { image ->
            state = state.copy(draft = state.draft.withCover(image), status = "封面已替换到草稿。点击“写入歌曲文件”保存。")
        })
    }

    fun removeCover() {
        if (!closed && !state.busy && state.loaded != null) state = state.copy(draft = state.draft.copy(artwork = emptyList()),
            error = false, status = "草稿中的全部内嵌图片已移除。点击“写入歌曲文件”保存。")
    }

    fun restoreCover() {
        val original = state.loaded ?: return
        if (!closed && !state.busy) state = state.copy(draft = state.draft.copy(artwork = original.metadata.artwork),
            error = false, status = "已恢复本次读取时的封面，其他草稿保留。")
    }

    fun applyImport() {
        val pending = state.onlineImport ?: return
        if (state.busy || state.loaded == null) return
        val updated = pending.apply(state.draft)
        val replacingLyrics = pending.lyricsSelected && pending.lyricsText != null && (!pending.fillMissing || state.draft.lyrics.isBlank())
        if (replacingLyrics) lyrics(updated.lyrics)
        state = state.copy(draft = updated, error = false,
            status = "所选字段、封面和歌词已按导入模式应用到草稿。请核对后写入歌曲文件。")
    }

    fun openSearch() {
        val loaded = state.loaded ?: return
        if (!closed && !state.busy) operations.openSearch(TrackQuery(
            title = state.draft[AudioMetadataField.TITLE].ifBlank { loaded.path.fileName.toString().substringBeforeLast('.') },
            artists = state.draft[AudioMetadataField.ARTIST].lineSequence().filter(String::isNotBlank).toList(),
            album = state.draft[AudioMetadataField.ALBUM], path = loaded.path.toString(),
        ))
    }

    fun importLyrics() {
        if (closed || state.busy || state.loaded == null) return
        val path = operations.chooseLyricsFile() ?: return
        run({ MetadataLyricsImport.read(path) }, { text ->
            lyrics(text)
            state = state.copy(status = "已导入 ${path.fileName}，点击“写入歌曲文件”保存。")
        })
    }

    fun save() = save(null)

    fun saveAndContinue() {
        val action = state.pendingDiscard ?: return
        save(action)
    }

    private fun save(continuation: (() -> Unit)?) {
        val allowed = if (continuation == null) state.canSave else state.canSaveAndContinue
        if (closed || !allowed) return
        val original = state.loaded ?: return
        val document = state.preview.takeIf { lyricsWritingEnabled && state.draft.lyrics.isNotBlank() }
        val request = MetadataWriteRequest(original, state.draft, document, state.format,
            document?.let { MetadataLyricsExport.losses(it, state.format) }.orEmpty(), continuation)
        if (request.losses.isNotEmpty()) state = state.copy(pendingWrite = request)
        else write(request)
    }

    fun confirmWrite() {
        val request = state.pendingWrite ?: return
        if (closed || state.busy) return
        state = state.copy(pendingWrite = null)
        write(request)
    }

    fun cancelWrite() { if (!state.busy) state = state.copy(pendingWrite = null, pendingDiscard = null) }

    private fun write(request: MetadataWriteRequest) {
        run({
            val edited = request.document?.let { request.edited.copy(lyrics = MetadataLyricsExport.encode(it, request.format)) } ?: request.edited
            if (edited.lyrics.isNotBlank() && edited.lyrics != request.original.metadata.lyrics) MetadataLyricsImport.preview(edited.lyrics)
            if (edited == request.original.metadata) AudioMetadataSaved(request.original) to false
            else store.save(request.original, edited) to true
        }, { (saved, written) ->
            loaded(saved.snapshot, request.format)
            if (written) {
                val refresh = runCatching { operations.afterSave(saved.snapshot.path) }.isSuccess
                state = state.copy(status = "已写入。" +
                    (if (refresh) "歌曲信息可重新扫描曲库后更新。" else "请切歌后重试歌词显示，并重新扫描曲库更新歌曲信息。") + saved.notice)
            } else state = state.copy(status = "内容已符合所选格式，无需重复写入。")
            request.continuation?.invoke()
        })
    }

    fun requestDiscard(action: () -> Unit) {
        if (closed || state.busy || state.pendingDiscard != null || state.pendingWrite != null) return
        if (state.dirty) state = state.copy(pendingDiscard = action)
        else action()
    }

    fun discard() {
        if (state.busy) return
        val action = state.pendingDiscard ?: return
        state = state.copy(pendingDiscard = null)
        action()
    }

    fun cancelDiscard() { if (!state.busy) state = state.copy(pendingDiscard = null) }

    private fun loaded(snapshot: AudioMetadataSnapshot, format: MetadataLyricsFormat = operations.lyricsFormat()) {
        state = MetadataEditorState(loaded = snapshot, draft = snapshot.metadata, format = format, lyricsWritingEnabled = lyricsWritingEnabled,
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
