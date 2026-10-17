package dev.gaboron.spwlyrics.integration.manualui

import dev.gaboron.spwlyrics.integration.composeui.DesktopToolWindow
import dev.gaboron.spwlyrics.integration.composeui.DesktopWindowContent

/** Composes the desktop frame and controller without owning search or persistence logic. */
internal class ManualSearchWindow(
    operations: ManualSearchOperations,
    onFailure: (String) -> Unit,
) : AutoCloseable {
    private val window = DesktopToolWindow("SPW Lyrics · 手动歌词搜索", "manual-window-position.json", onFailure) {
        val controller = ManualSearchController(operations)
        controller.refreshTrack()
        DesktopWindowContent({ ManualSearchContent(controller) }, controller::close)
    }

    fun open() = window.open()
    override fun close() = window.close()
}
