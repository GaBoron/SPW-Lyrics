package dev.gaboron.spwlyrics.integration

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.nio.file.Files
import kotlin.io.path.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shares a safe remembered position between the WinUI window and Swing fallback. */
internal object ManualWindowPlacement {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = (System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::Path)
        ?: Path(System.getProperty("user.home")))
        .resolve("SPW Lyrics")
        .resolve("manual-window-position.json")

    fun restore(window: Window): Boolean = runCatching {
        if (!Files.isRegularFile(file)) return false
        val placement = json.decodeFromString<Placement>(Files.readString(file))
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map { device ->
                val bounds = device.defaultConfiguration.bounds
                val insets = window.toolkit.getScreenInsets(device.defaultConfiguration)
                Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    bounds.width - insets.left - insets.right,
                    bounds.height - insets.top - insets.bottom,
                )
            }
        val area = screens.minByOrNull { distanceSquared(it, placement.x, placement.y) } ?: return false
        val x = placement.x.coerceIn(area.x, maxOf(area.x, area.x + area.width - window.width))
        val y = placement.y.coerceIn(area.y, maxOf(area.y, area.y + area.height - window.height))
        window.setLocation(x, y)
        true
    }.getOrDefault(false)

    fun save(window: Window) {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(Placement(window.x, window.y)))
        }
    }

    private fun distanceSquared(area: Rectangle, x: Int, y: Int): Long {
        val nearestX = x.coerceIn(area.x, area.x + area.width)
        val nearestY = y.coerceIn(area.y, area.y + area.height)
        val dx = (x - nearestX).toLong()
        val dy = (y - nearestY).toLong()
        return dx * dx + dy * dy
    }

    @Serializable
    private data class Placement(val x: Int, val y: Int)
}
