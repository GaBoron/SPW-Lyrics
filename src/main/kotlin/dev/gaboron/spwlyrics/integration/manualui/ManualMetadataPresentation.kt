package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.domain.AudioMetadataField
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import java.util.Locale

internal data class ManualMetadataGroup(val title: String, val entries: List<Pair<String, String>>)

/** Keep candidate song fields and lyric-file metadata separate when their values disagree. */
internal object ManualMetadataPresentation {
    fun groups(candidate: LyricsCandidate?, lyrics: Map<String, List<String>>): List<ManualMetadataGroup> = buildList {
        if (candidate != null) {
            val fields = linkedMapOf("title" to listOf(candidate.title), "artist" to candidate.artists,
                "album" to listOf(candidate.album), "source" to listOf(candidate.source.displayName))
            candidate.durationMs?.let { fields["duration"] = listOf(String.format(Locale.ROOT, "%d:%02d", it / 60000, it / 1000 % 60)) }
            candidate.externalIds.forEach { (key, value) -> fields[key] = listOf(value) }
            candidate.metadata.forEach { (key, values) -> if (values.any(String::isNotBlank)) fields[key] = values }
            entries(fields).takeIf { it.isNotEmpty() }?.let { add(ManualMetadataGroup("歌曲信息", it)) }
        }
        entries(lyrics).takeIf { it.isNotEmpty() }?.let { add(ManualMetadataGroup("歌词元数据", it)) }
    }

    private fun entries(fields: Map<String, List<String>>) = fields.mapNotNull { (key, values) ->
        values.map(String::trim).filter(String::isNotBlank).distinct().joinToString("\n")
            .takeIf(String::isNotBlank)?.let { label(key) to it }
    }

    private fun label(key: String): String = labels[key.lowercase(Locale.ROOT)]
        ?: if (key.startsWith("agent.")) "演唱者 ${key.substringAfter('.')}" else key

    private val labels = AudioMetadataField.entries.associate { it.onlineKey to it.label } + mapOf(
        "musicname" to "歌曲名", "artists" to "歌手", "ti" to "歌曲名", "ar" to "歌手", "al" to "专辑",
        "by" to "制作者", "re" to "制作工具", "ve" to "工具版本", "offset" to "歌词偏移（ms）",
        "source" to "来源", "duration" to "时长", "length" to "时长", "desc" to "说明", "credits" to "制作信息",
        "author" to "作者", "songwriters" to "词曲作者", "cover_url" to "封面地址",
        "ncmusicid" to "网易云歌曲 ID", "ncmmusicid" to "网易云歌曲 ID", "neteaseid" to "网易云歌曲 ID",
        "qqmusicid" to "QQ 音乐歌曲 ID", "qqmusicmid" to "QQ 音乐歌曲 MID", "spotifyid" to "Spotify 歌曲 ID",
        "applemusicid" to "Apple Music 歌曲 ID", "isrc" to "ISRC",
        "ttmlauthorgithub" to "TTML 作者 GitHub", "ttmlauthorgithublogin" to "TTML 作者 GitHub 用户名",
        "ttmlauthorgithubuid" to "TTML 作者 GitHub ID", "ttmlauthorinfo" to "TTML 作者信息",
    )
}
