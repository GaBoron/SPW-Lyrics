package dev.gaboron.spwlyrics.integration

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

class CacheFolderOpener(
    private val cacheDirectory: Path,
    private val openDirectory: (Path) -> Unit = ::openWithDesktop,
) {
    fun open(): Boolean = runCatching {
        Files.createDirectories(cacheDirectory)
        openDirectory(cacheDirectory)
    }.isSuccess

    private companion object {
        fun openWithDesktop(directory: Path) {
            check(Desktop.isDesktopSupported()) { "Desktop API is unavailable" }
            val desktop = Desktop.getDesktop()
            check(desktop.isSupported(Desktop.Action.OPEN)) { "Opening folders is unsupported" }
            desktop.open(directory.toFile())
        }
    }
}
