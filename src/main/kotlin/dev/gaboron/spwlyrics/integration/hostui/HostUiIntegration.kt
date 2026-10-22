@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A fresh scan for each enable. No version matrix, disk index, old targets or fallback hooks. */
internal class HostUiIntegration(private val workspace: LyricsWorkspace, private val notify: (String) -> Unit) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "spw-lyrics-host-ui").apply { isDaemon = true } }
    @Volatile private var stopped = false
    private var hooks: HostUiHooks? = null

    fun start() {
        worker.execute {
            val result = runCatching {
                check(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) { "请授予类变换权限后重新启用插件" }
                val host = WorkshopApi.instance.javaClass
                val source = Path.of(checkNotNull(host.protectionDomain.codeSource).location.toURI())
                val targets = HostUiDiscovery.discover(HostBytecodeIndex.read(source))
                HostUiBridge(targets, host.classLoader, workspace)
            }
            EventQueue.invokeLater {
                if (stopped) return@invokeLater
                result.onSuccess { bridge ->
                    runCatching {
                        check(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) { "类变换权限已撤销" }
                        HostUiHooks(bridge, workspace).also { it.install(); hooks = it }
                        workspace.beforeOpen = bridge::enter
                        workspace.available = true
                    }.onFailure(::failed)
                }.onFailure(::failed)
            }
        }
        worker.scheduleWithFixedDelay({
            if (!stopped && !WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) EventQueue.invokeLater {
                if (hooks != null) { hooks?.close(); hooks = null; workspace.unavailable() }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun failed(error: Throwable) {
        System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "歌词管理主窗口接入失败", error)
        notify("歌词管理主窗口入口未启用：${error.cause?.message ?: error.message}。请检查权限或反馈 SPW 版本及异常。")
    }

    override fun close() {
        stopped = true
        worker.shutdownNow()
        val release = { hooks?.close(); hooks = null; workspace.unavailable() }
        if (EventQueue.isDispatchThread()) release() else EventQueue.invokeLater(release)
    }
}
