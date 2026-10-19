package dev.gaboron.spwlyrics.storage

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

/** Short-lived transaction files live outside the music library and are recycled together. */
internal class AudioTagWorkspace(extension: String) {
    private val directory = Files.createTempDirectory("spw-lyrics-tags-")
    val stage: Path = directory.resolve("edited.$extension")
    val recovery: Path = directory.resolve("original.$extension")

    fun recycle(): String {
        val recycled = runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                Desktop.getDesktop().moveToTrash(directory.toFile())
        }.getOrDefault(false)
        return if (recycled) "" else "系统回收站不可用，临时事务文件保留在：$directory。"
    }
}
