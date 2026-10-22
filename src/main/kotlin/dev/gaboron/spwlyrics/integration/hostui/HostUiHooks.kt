@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class, androidx.compose.runtime.InternalComposeApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.runtime.currentComposer
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.hook.MethodHook
import com.xuncorp.spw.workshop.api.hook.MethodHookParam
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace

/** Registers a complete set or rolls it back. MethodHookParam never escapes a synchronous callback. */
internal class HostUiHooks(private val bridge: HostUiBridge, private val workspace: LyricsWorkspace) : AutoCloseable {
    private val handles = mutableListOf<AutoCloseable>()

    fun install() {
        try {
            for (key in bridge.targets.navigation() + bridge.targets.back()) {
                val reflected = bridge.method(key)
                handles += WorkshopApi.hookRegistrar.hookMethod(key.owner().replace('/', '.'), key.name(),
                    reflected.parameterTypes.map { it.name }, object : MethodHook(80) {
                        override fun before(call: MethodHookParam) {
                            if (bridge.replaying || workspace.session == null) return
                            val receiver = call.thisObject
                            val args = call.args.copyOf()
                            call.setResult(null)
                            workspace.requestLeave {
                                if (key != bridge.targets.back()) bridge.replay { reflected.invoke(receiver, *args) }
                            }
                        }
                    })
            }
            val page = bridge.targets.page()
            handles += WorkshopApi.hookRegistrar.afterComposable(page.target().owner().replace('/', '.'),
                page.target().name(), page.caller().replace('/', '.'), parameterTypes = emptyList()) {
                bridge.capture(currentComposer)
                HostLyricsPage(workspace)
            }
            val entry = bridge.targets.captionEntry()
            handles += WorkshopApi.hookRegistrar.afterComposable(entry.target().owner().replace('/', '.'),
                entry.target().name(), entry.caller().replace('/', '.'), parameterTypes = emptyList()) { bridge.Entry() }
        } catch (failure: Throwable) { close(); throw failure }
    }

    override fun close() {
        handles.asReversed().forEach { handle ->
            runCatching { handle.close() }.onFailure {
                System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "清理歌词管理 Hook 失败", it)
            }
        }
        handles.clear()
    }
}
