@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

/** Reuses verified descriptors; a failed cached registration rolls back before one fresh scan. */
internal class HostUiIntegration(
    private val workspace: LyricsWorkspace,
    private val notify: (String) -> Unit,
    private val performance: PluginPerformance,
    private val cache: HostUiTargetCache,
) : AutoCloseable {
    private data class Prepared(
        val targets: HostUiTargets,
        val source: HostUiSource?,
        val cached: Boolean,
        val bridge: HostUiBridge,
        val menu: Result<HostPlayerMenu>,
    )
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "spw-lyrics-host-ui").apply { isDaemon = true } }
    @Volatile private var stopped = false
    private var hooks: HostUiHooks? = null
    private var playerMenu: HostPlayerMenu? = null
    private val permissionPending = AtomicBoolean()
    private var startedAt = 0L

    private fun checkActive() {
        if (stopped || Thread.currentThread().isInterrupted) throw CancellationException()
        if (!WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) {
            throw PluginPermissionDeniedException("spw-lyrics", PluginPermission.CLASS_TRANSFORM)
        }
    }

    fun start() {
        startedAt = System.nanoTime()
        submit(useCache = true)
        worker.scheduleWithFixedDelay({
            if (!stopped && !WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM) &&
                permissionPending.compareAndSet(false, true)) EventQueue.invokeLater {
                permissionPending.set(false)
                if (!stopped && !WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) release()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun submit(useCache: Boolean, invalidate: Boolean = false) = execute {
        val result = runCatching {
            checkActive()
            if (invalidate) cache.invalidate()
            prepare(useCache)
        }
        if (!stopped) EventQueue.invokeLater {
            if (stopped) return@invokeLater
            result.onSuccess(::install).onFailure {
                if (it !is CancellationException) failed(it)
            }
        }
    }

    private fun prepare(useCache: Boolean): Prepared {
        val host = WorkshopApi.instance.javaClass
        val source = runCatching { HostUiSource.read(HostBytecodeIndex.source(host), host.name) }.getOrNull()
        if (useCache && source != null) {
            val cached = performance.measure(PluginPerformance.Operation.HOST_CACHE_READ) { cache.read(source) }
            if (cached != null) {
                try {
                    return prepareTargets(host, cached, source, cached = true)
                } catch (error: Throwable) {
                    if (error is VirtualMachineError) throw error
                    checkActive()
                    if (error is PluginPermissionDeniedException) throw error
                    cacheFailed(error)
                    cache.invalidate()
                }
            }
        }
        return performance.measure(PluginPerformance.Operation.HOST_SCAN) {
            checkActive()
            val index = HostBytecodeIndex.read(host)
            checkActive()
            val main = HostUiDiscovery.discover(index)
            checkActive()
            val popup = runCatching { HostPopupMaterialDiscovery.discover(index) }.onFailure {
                System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "宿主菜单材质未接入，使用普通菜单", it)
            }.getOrNull()
            checkActive()
            val menu = runCatching { HostPlayerMenuDiscovery.discover(index) }
            prepareTargets(host, HostUiTargets(main, popup, menu.getOrNull()), source, cached = false, menuFailure = menu.exceptionOrNull())
        }
    }

    private fun prepareTargets(
        host: Class<*>,
        targets: HostUiTargets,
        source: HostUiSource?,
        cached: Boolean,
        menuFailure: Throwable? = null,
    ): Prepared = performance.measure(PluginPerformance.Operation.HOST_PREPARE) {
        checkActive()
        val bridge = HostUiBridge(targets.main, host.classLoader, workspace, targets.popup)
        val menu = runCatching {
            if (menuFailure != null) throw menuFailure
            HostPlayerMenu(checkNotNull(targets.menu), host.classLoader, workspace) { bridge.isDarkTheme() }
        }
        if (cached) menu.getOrThrow()
        Prepared(targets, source, cached, bridge, menu)
    }

    private fun install(prepared: Prepared) {
        val operation = if (prepared.cached) PluginPerformance.Operation.HOST_CACHE_INSTALL else PluginPerformance.Operation.HOST_INSTALL
        val result = runCatching {
            performance.measure(operation) {
                checkActive()
                val activeHooks = HostUiHooks(prepared.bridge, workspace)
                hooks = activeHooks
                activeHooks.install()
                var menuInstalled = false
                prepared.menu.onSuccess { entry ->
                    playerMenu = entry
                    val installed = runCatching {
                        entry.install()
                        menuInstalled = true
                    }
                    if (prepared.cached) installed.getOrThrow()
                    else installed.onFailure {
                        entry.close()
                        playerMenu = null
                        menuFailed(it)
                    }
                }.onFailure {
                    if (prepared.cached) throw it else menuFailed(it)
                }
                checkActive()
                workspace.beforeOpen = prepared.bridge::enter
                workspace.available = true
                if (!prepared.cached && menuInstalled && prepared.targets.complete) execute {
                    checkActive()
                    prepared.source?.let { cache.write(it, prepared.targets) }
                }
            }
        }
        result.onSuccess {
            performance.record(PluginPerformance.Operation.HOST_READY, System.nanoTime() - startedAt)
            performance.report(if (prepared.cached) "host ready: cache" else "host ready: scan")
        }.onFailure { error ->
            val rollback = runCatching(::release)
            if (rollback.isFailure) {
                error.addSuppressed(checkNotNull(rollback.exceptionOrNull()))
                if (!stopped) failed(error)
            } else if (!stopped && error !is CancellationException && error !is PluginPermissionDeniedException &&
                prepared.cached && WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)) {
                cacheFailed(error)
                submit(useCache = false, invalidate = true)
            } else if (!stopped && error !is CancellationException) failed(error)
        }
    }

    private fun cacheFailed(error: Throwable) {
        performance.record(PluginPerformance.Operation.HOST_CACHE_FALLBACK, 0)
        System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "宿主 Hook 缓存未能接入，将重新扫描", error)
    }

    private fun execute(action: () -> Unit) {
        if (stopped) return
        try {
            worker.execute workerTask@{
                if (stopped) return@workerTask
                runCatching(action).onFailure {
                    if (!stopped && it !is CancellationException) System.getLogger("SPW Lyrics")
                        .log(System.Logger.Level.WARNING, "宿主 Hook 后台任务失败", it)
                }
            }
        } catch (error: RejectedExecutionException) {
            if (!stopped) throw error
        }
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
        var failure: Throwable? = null
        runCatching {
            playerMenu?.close()
            playerMenu = null
        }.onFailure { failure = it }
        runCatching {
            hooks?.close()
            hooks = null
        }.onFailure {
            if (failure == null) failure = it else failure.addSuppressed(it)
        }
        workspace.unavailable()
        failure?.let { throw it }
    }
}
