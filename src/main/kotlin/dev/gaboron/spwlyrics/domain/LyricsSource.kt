package dev.gaboron.spwlyrics.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LyricsSource(val displayName: String, val priority: Int) {
    AMLL("AMLL TTML DB", 0),
    QQ("QQ音乐", 1),
    KUGOU("酷狗音乐", 2),
    NETEASE("网易云音乐", 3),
    LOCAL("本地歌词", 4),
}
