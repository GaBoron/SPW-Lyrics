package dev.gaboron.spwlyrics.storage

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/** Remembers choices made in the workspace, independently of plugin configuration and lyric caches. */
internal class LyricsUiStateStore(private val path: Path) {
    private var values = runCatching {
        Properties().apply {
            if (Files.isRegularFile(path)) Files.newBufferedReader(path).use(::load)
        }
    }.getOrElse { Properties() }

    @Synchronized
    fun lyricsFormat(): String = values.getProperty("lyricsFormat", "ttml")

    @Synchronized
    fun candidateSort(): String = values.getProperty("candidateSort", "PREFERENCE")

    @Synchronized
    fun rememberLyricsFormat(value: String): Boolean = write("lyricsFormat", value)

    @Synchronized
    fun rememberCandidateSort(value: String): Boolean = write("candidateSort", value)

    private fun write(key: String, value: String): Boolean {
        val updated = Properties().apply {
            putAll(this@LyricsUiStateStore.values)
            setProperty(key, value)
        }
        return runCatching {
            Files.createDirectories(path.parent)
            val pending = path.resolveSibling(path.fileName.toString() + ".pending")
            Files.newBufferedWriter(pending).use { updated.store(it, "Lyrics workspace UI state") }
            try {
                Files.move(pending, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending, path, REPLACE_EXISTING)
            }
            values = updated
            true
        }.getOrDefault(false)
    }
}
