package dev.gaboron.spwlyrics.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LyricsSource(val displayName: String, val priority: Int, val hasLyrics: Boolean = true) {
    AMLL("AMLL TTML DB", 0),
    QQ("QQ音乐", 1),
    SODA("汽水音乐", 2),
    KUGOU("酷狗音乐", 3),
    NETEASE("网易云音乐", 4),
    APPLE("Apple Music", 5),
    LRCSHARE("LrcShare", 6),
    MUSICBRAINZ("MusicBrainz（元数据）", 7, false),
    LOCAL("本地歌词", 8),
}
