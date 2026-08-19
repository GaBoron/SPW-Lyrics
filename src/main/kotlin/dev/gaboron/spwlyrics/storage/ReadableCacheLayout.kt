package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class ReadableCacheLayout(private val root: Path) {
    private val lyricsDirectory = root.resolve("歌词")
    private val overridesDirectory = root.resolve("手动匹配")

    init {
        Files.createDirectories(lyricsDirectory)
        Files.createDirectories(overridesDirectory)
        purgeLegacyFiles("lyrics-*.json")
        purgeLegacyFiles("override-*.json")
        writeInstructions()
    }

    fun lyrics(query: TrackQuery): Path = lyricsDirectory.resolve(fileName(query))

    fun override(query: TrackQuery): Path = overridesDirectory.resolve(fileName(query))

    private fun fileName(query: TrackQuery): String {
        val artist = query.artists.take(3).joinToString("、").ifBlank { "未知歌手" }
        val title = query.title.ifBlank { "未知曲名" }
        val readable = sanitize("$artist - $title").take(MAX_READABLE_LENGTH).trimEnd(' ', '.')
        return "$readable [${query.key.take(KEY_SUFFIX_LENGTH)}].json"
    }

    private fun sanitize(value: String): String = value
        .replace(INVALID_FILE_NAME_CHARS, "_")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .ifBlank { "未知歌曲" }

    private fun purgeLegacyFiles(pattern: String) {
        Files.newDirectoryStream(root, pattern).use { paths ->
            paths.filter(Files::isRegularFile).forEach(Files::deleteIfExists)
        }
    }

    private fun writeInstructions() {
        val instructions = root.resolve("缓存说明.txt")
        if (Files.exists(instructions)) return
        runCatching {
            Files.writeString(
                instructions,
                """
                    SPW Lyrics 缓存目录

                    “歌词”保存自动及手动加载的歌词，删除后会在下次播放时重新获取。
                    “手动匹配”保存手动选择或使用本地歌词的记录，删除后会恢复自动匹配。
                    “AMLL 索引”保存 AMLL 歌词源的本地索引，由插件自动检查和更新。
                    歌词与手动匹配目录中的 JSON 文件都可以按“歌手 - 曲名”直接查找并安全删除。
                """.trimIndent(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
            )
        }
    }

    private companion object {
        val INVALID_FILE_NAME_CHARS = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")
        const val MAX_READABLE_LENGTH = 120
        const val KEY_SUFFIX_LENGTH = 12
    }
}
