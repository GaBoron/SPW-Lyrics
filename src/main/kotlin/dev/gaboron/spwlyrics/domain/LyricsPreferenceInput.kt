package dev.gaboron.spwlyrics.domain

/** Human-readable settings lists use the same order as automatic and manual selection. */
internal object LyricsPreferenceInput {
    const val DEFAULT_QUALITIES = "逐字, 逐行, 普通"
    val DEFAULT_SOURCES = LyricsPreferences().sources.joinToString(", ") { if (it == LyricsSource.MUSICBRAINZ) "MusicBrainz" else it.displayName }
    private val qualityNames = mapOf("逐字" to LyricsQuality.KARAOKE_SYNCED, "逐行" to LyricsQuality.LINE_SYNCED, "普通" to LyricsQuality.PLAIN)
    private val sourceNames = mapOf("amll" to LyricsSource.AMLL, "qq" to LyricsSource.QQ, "网易" to LyricsSource.NETEASE, "网易云" to LyricsSource.NETEASE,
        "酷狗" to LyricsSource.KUGOU, "汽水" to LyricsSource.SODA, "apple" to LyricsSource.APPLE, "musicbrainz" to LyricsSource.MUSICBRAINZ)

    fun parse(mode: String, qualities: String, sources: String): LyricsPreferences {
        val quality = values(qualities, "歌词类型") { name -> qualityNames[name] ?: LyricsQuality.entries.find { it.name.equals(name, true) } }
        val source = values(sources, "来源") { name -> sourceNames[name.lowercase()] ?: LyricsSource.entries.find {
            it != LyricsSource.LOCAL && (it.name.equals(name, true) || it.displayName.equals(name, true))
        } }
        val priorityMode = LyricsPriorityMode.entries.find { it.configValue == mode } ?: error("未知的歌词选择依据")
        return LyricsPreferences(priorityMode, quality, source)
    }

    private fun <T> values(text: String, label: String, resolve: (String) -> T?): List<T> {
        val tokens = text.split(Regex("[,，、;；\\r\\n>→]+")).map(String::trim).filter(String::isNotEmpty)
        require(tokens.isNotEmpty()) { "${label}顺序不能为空，可点击“重置歌词排序”。" }
        val values = tokens.map { resolve(it) ?: error("无法识别${label}“$it”，请检查名称或重置歌词排序。") }
        require(values.distinct().size == values.size) { "${label}顺序有重复项，请删除重复名称或重置。" }
        return values
    }
}
