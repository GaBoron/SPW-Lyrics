package dev.gaboron.spwlyrics.codec

internal enum class MetadataLyricsFormat(val label: String, val detail: String) {
    TTML("TTML", "保留逐字、声部、翻译和音译；保留原 TTML 节点，外层结构自动换行。"),
    LRC("LRC", "保留逐行时间、翻译和音译；不包含逐字时间、声部和注音。"),
    ENHANCED_LRC("增强 LRC", "用尖括号记录逐字时间；无逐字时间的行保留逐行时间。"),
    SPL("SPL", "椒盐歌词格式，包含逐字标记和显式行结束时间。"),
}
