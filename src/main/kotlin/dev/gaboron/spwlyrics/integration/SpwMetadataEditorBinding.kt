package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.WorkshopApi

/** Expose an unbound action; SPW owns the user's shortcut and global-binding preferences. */
internal class SpwMetadataEditorBinding(private val onPressed: () -> Unit) : AutoCloseable {
    private var registration: AutoCloseable? = null

    fun register(): Boolean {
        if (!WorkshopApi.manager.isPermissionGranted(PluginPermission.KEY_BINDINGS)) return false
        registration = WorkshopApi.manager.keyBindingManager.register(
            actionId = "open-metadata-editor",
            title = "打开歌曲元数据编辑",
            defaultShortcut = null,
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
