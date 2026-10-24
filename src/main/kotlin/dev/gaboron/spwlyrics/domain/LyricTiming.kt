package dev.gaboron.spwlyrics.domain

internal data class TimingCursor(val lineId: Int, val word: Int)
internal enum class TimingMark { START, CONNECT, END }
internal enum class TimingJudge(val label: String) { DOWN("按下时刻"), UP("松开时刻"), MIDDLE("按下与松开的中点") }
internal enum class TimingSplit(val label: String) { CHARACTERS("CJK 逐字 / 英文音节"), WHITESPACE("按空格保留整词") }
internal data class TimingOptions(val smartFirst: Boolean = false, val smartLast: Boolean = false, val offsetMs: Int = 0)
internal data class TimingProgress(val cursor: TimingCursor? = null, val firstStarted: TimingCursor? = null, val emptyBeat: Int = 0)
internal data class TimingChange(val lines: Map<Int, EditableLyricLine>, val progress: TimingProgress)
