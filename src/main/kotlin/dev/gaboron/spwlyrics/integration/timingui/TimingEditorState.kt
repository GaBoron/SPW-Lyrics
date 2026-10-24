package dev.gaboron.spwlyrics.integration.timingui

import dev.gaboron.spwlyrics.domain.*

internal data class TimingEditorState(
    val lines: List<EditableLyricLine> = emptyList(),
    val progress: TimingProgress = TimingProgress(),
    val options: TimingOptions = TimingOptions(),
    val judge: TimingJudge = TimingJudge.DOWN,
    val skipped: Set<Int> = emptySet(),
    val emptyBeats: Map<TimingCursor, Int> = emptyMap(),
    val available: Boolean = false,
    val busy: Boolean = false,
    val preparing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String = "请先在 SPW 播放正在编辑的歌曲，再点击歌词区域使用打轴快捷键。",
    val error: Boolean = false,
) {
    val canRecord get() = available && !busy && !preparing && progress.cursor != null
    val cursor get() = progress.cursor
}
