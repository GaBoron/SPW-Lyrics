package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import dev.gaboron.spwlyrics.integration.ManualWindowPlacement
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

internal class DesktopWindowContent(
    val render: @Composable () -> Unit,
    val close: () -> Unit,
    val requestClose: (() -> Unit) -> Unit = { it() },
)

/** Owns a single native desktop frame, with Compose as its entire content. */
internal class DesktopToolWindow(
    private val title: String,
    private val positionFile: String,
    private val onFailure: (String) -> Unit,
    private val preferredWidth: Int = 1180,
    private val preferredHeight: Int = 720,
    private val createContent: () -> DesktopWindowContent,
) : AutoCloseable {
    @Volatile private var closed = false
    private var window: ComposeWindow? = null

    fun open(afterOpen: (() -> Unit)? = null) = EventQueue.invokeLater {
        if (closed) return@invokeLater
        window?.takeIf { it.isDisplayable }?.let {
            it.isVisible = true
            it.toFront()
            it.requestFocus()
            afterOpen?.invoke()
            return@invokeLater
        }
        var frame: ComposeWindow? = null
        var content: DesktopWindowContent? = null
        runCatching {
            ComposeDesktopRuntime.load()
            val shown = ComposeWindow()
            frame = shown
            val session = createContent()
            content = session
            shown.title = title
            shown.defaultCloseOperation = 0 // Dispose explicitly, never exit the host JVM.
            val scale = shown.graphicsConfiguration.defaultTransform.scaleX
            val screen = shown.graphicsConfiguration.bounds
            val insets = shown.toolkit.getScreenInsets(shown.graphicsConfiguration)
            val availableWidth = screen.width - insets.left - insets.right
            val availableHeight = screen.height - insets.top - insets.bottom
            shown.minimumSize = Dimension(minOf((1060 * scale).toInt(), availableWidth), minOf((650 * scale).toInt(), availableHeight))
            shown.setSize(minOf((preferredWidth * scale).toInt(), availableWidth), minOf((preferredHeight * scale).toInt(), availableHeight))
            if (!ManualWindowPlacement.restore(shown, positionFile)) shown.setLocationRelativeTo(null)
            shown.setContent { DesktopTheme { session.render() } }
            shown.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) { session.requestClose { shown.dispose() } }
                override fun windowClosed(event: WindowEvent) {
                    ManualWindowPlacement.save(shown, positionFile)
                    session.close()
                    if (window === shown) window = null
                }
            })
            window = shown
            shown.isVisible = true
            afterOpen?.invoke()
        }.onFailure {
            content?.close?.invoke()
            frame?.dispose()
            window = null
            onFailure("窗口未能打开：${it.message ?: "未知错误"}")
        }
    }

    override fun close() {
        closed = true
        EventQueue.invokeLater { window?.dispose(); window = null }
    }
}
