@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

/** A fresh scan for each enable. No version matrix, disk index, old targets or fallback hooks. */
internal class HostUiIntegration(
    private val workspace: LyricsWorkspace,
    private val notify: (String) -> Unit,
    private val performance: PluginPerformance,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "spw-lyrics-host-ui").apply { isDaemon = true } }
    @Volatile private var stopped = false
    private var hooks: HostUiHooks? = null
    private var playerMenu: HostPlayerMenu? = null
    private val permissionPending = AtomicBoolean()

    private fun checkActive() {
        if (stopped || Thread.currentThread().isInterrupted) throw CancellationException()
        check(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) { "请授予类变换权限后重新启用插件" }
    }

    fun start() {
        worker.execute {
            val result = runCatching {
                performance.measure(PluginPerformance.Operation.HOST_SCAN) {
                    checkActive()
                    val host = WorkshopApi.instance.javaClass
                    val index = HostBytecodeIndex.read(host)
                    checkActive()
                    val targets = HostUiDiscovery.discover(index)
                    checkActive()
                    val popup = runCatching { HostPopupMaterialDiscovery.discover(index) }.onFailure {
                        System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "宿主菜单材质未接入，使用普通菜单", it)
                    }.getOrNull()
                    checkActive()
                    val bridge = HostUiBridge(targets, host.classLoader, workspace, popup)
                    bridge to runCatching {
                        HostPlayerMenu(HostPlayerMenuDiscovery.discover(index), host.classLoader, workspace) { bridge.isDarkTheme() }
                    }
                }
            }
            EventQueue.invokeLater {
                if (stopped) return@invokeLater
                result.onSuccess { (bridge, menu) ->
                    runCatching {
                        performance.measure(PluginPerformance.Operation.HOST_INSTALL) {
                            checkActive()
                            HostUiHooks(bridge, workspace).also { it.install(); hooks = it }
                            workspace.beforeOpen = bridge::enter
                            workspace.available = true
                            menu.onSuccess { entry ->
                                runCatching { entry.install(); playerMenu = entry }.onFailure {
                                    entry.close()
                                    menuFailed(it)
                                }
                            }.onFailure(::menuFailed)
                            checkActive()
                        }
                    }.onFailure {
                        release()
                        if (!stopped && it !is CancellationException) failed(it)
                    }
                }.onFailure(::failed)
            }
        }
        worker.scheduleWithFixedDelay({
            if (!stopped && !WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM) &&
                permissionPending.compareAndSet(false, true)) EventQueue.invokeLater {
                permissionPending.set(false)
                if (!stopped && !WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) release()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun failed(error: Throwable) {
        System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "歌词管理主窗口接入失败", error)
        notify("歌词管理主窗口入口未启用：${error.cause?.message ?: error.message}。请检查权限或反馈 SPW 版本及异常。")
    }

    private fun menuFailed(error: Throwable) {
        System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "播放器歌词菜单接入失败", error)
        notify("播放器歌词工具菜单未启用，请从顶部歌词管理进入。原因：${error.cause?.message ?: error.message}")
    }

    override fun close() {
        stopped = true
        worker.shutdownNow()
        if (EventQueue.isDispatchThread()) release() else EventQueue.invokeLater(::release)
    }

    private fun release() {
        try { playerMenu?.close() } finally {
            playerMenu = null
            try { hooks?.close() } finally {
                hooks = null
                workspace.unavailable()
            }
        }
    }
}
