package dev.gaboron.spwlyrics.codec

internal enum class MetadataLyricsFormat(val storageValue: String, val label: String, val detail: String) {
    TTML("ttml", "TTML", "保留逐字、声部、翻译和音译；保留原 TTML 节点，外层结构自动换行。"),
    LRC("lrc", "LRC", "保留逐行时间、翻译和音译；不包含逐字时间、声部和注音。"),
    ENHANCED_LRC("enhanced_lrc", "增强 LRC", "用尖括号记录逐字时间；无逐字时间的行保留逐行时间。"),
    SPL("spl", "SPL", "椒盐歌词格式，包含逐字标记和显式行结束时间。");

    companion object {
        fun fromStorageValue(value: String): MetadataLyricsFormat = entries.firstOrNull { it.storageValue == value } ?: TTML
    }
}
