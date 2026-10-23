package dev.gaboron.spwlyrics.domain

enum class LyricsPriorityMode(val configValue: String) { TYPE_FIRST("type_first"), SOURCE_FIRST("source_first") }

/** One immutable snapshot controls search, selection, cache reuse and later upgrades. */
data class LyricsPreferences(
    val mode: LyricsPriorityMode = LyricsPriorityMode.TYPE_FIRST,
    val qualities: List<LyricsQuality> = listOf(LyricsQuality.KARAOKE_SYNCED, LyricsQuality.LINE_SYNCED, LyricsQuality.PLAIN),
    val sources: List<LyricsSource> = LyricsSource.entries.filter { it != LyricsSource.LOCAL },
) {
    val key: String get() = listOf(mode.configValue, qualities.joinToString(",") { it.name }, sources.joinToString(",") { it.name }).joinToString("|")
    fun sourceRank(source: LyricsSource): Int = sources.indexOf(source).takeIf { it >= 0 } ?: Int.MAX_VALUE
    fun qualityRank(quality: LyricsQuality): Int = qualities.indexOf(quality).takeIf { it >= 0 } ?: Int.MAX_VALUE
    fun compare(leftSource: LyricsSource, leftQuality: LyricsQuality, rightSource: LyricsSource, rightQuality: LyricsQuality): Int {
        val source = sourceRank(leftSource).compareTo(sourceRank(rightSource))
        val quality = qualityRank(leftQuality).compareTo(qualityRank(rightQuality))
        return if (mode == LyricsPriorityMode.TYPE_FIRST) quality.takeIf { it != 0 } ?: source else source.takeIf { it != 0 } ?: quality
    }
    fun canImprove(document: LyricsDocument): Boolean = sources.any { source ->
        source != LyricsSource.LOCAL && source.hasLyrics && compare(source, qualities.first(), document.source, document.quality) < 0
    }
}
