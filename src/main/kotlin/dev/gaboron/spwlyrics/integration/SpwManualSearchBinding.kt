package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.ActionShortcut
import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.WorkshopApi
import java.awt.event.KeyEvent

/** Registration belongs to the plugin start thread; SPW owns remapping and conflict handling. */
internal class SpwManualSearchBinding(private val onPressed: () -> Unit) : AutoCloseable {
    private var registration: AutoCloseable? = null

    fun register(): Boolean {
        if (!WorkshopApi.manager.isPermissionGranted(PluginPermission.KEY_BINDINGS)) return false
        registration = WorkshopApi.manager.keyBindingManager.register(
            actionId = "open-lyrics-search",
            title = "打开歌词搜索",
            defaultShortcut = ActionShortcut(KeyEvent.VK_M, ActionShortcut.CONTROL or ActionShortcut.SHIFT),
            hasGlobal = true,
            handler = Runnable(onPressed),
        )
        return true
    }

    override fun close() {
        registration?.close()
        registration = null
    }
}
