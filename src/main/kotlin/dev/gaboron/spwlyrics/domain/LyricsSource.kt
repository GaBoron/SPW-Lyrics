package dev.gaboron.spwlyrics.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LyricsSource(val displayName: String, val priority: Int) {
    AMLL("AMLL TTML DB", 0),
    APPLE_MUSIC("Apple Music", 1),
    QQ("QQ音乐", 2),
    KUGOU("酷狗音乐", 3),
    NETEASE("网易云音乐", 4),
    LOCAL("本地歌词", 5),
}
