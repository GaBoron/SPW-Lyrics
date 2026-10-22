@file:OptIn(androidx.compose.runtime.InternalComposeApi::class, com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.hook.ContentHook
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon
import dev.gaboron.spwlyrics.integration.workspace.LyricsToolPage
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.lang.invoke.MethodType

/** Appends native player actions through the host's DSL; the original builder still runs first. */
internal class HostPlayerMenu(private val targets: HostPlayerMenuDiscovery.Targets, loader: ClassLoader,
    private val workspace: LyricsWorkspace, private val isDark: @Composable () -> Boolean) : AutoCloseable {
    private val scope = Class.forName(targets.scope().replace('/', '.'), false, loader)
    private val item = scope.methods.single { method -> method.parameterTypes.map { it.name } ==
        listOf("kotlin.jvm.functions.Function0", "boolean", "kotlin.jvm.functions.Function2") }.also {
            check(it.trySetAccessible()) { "播放器菜单构建器不可访问" }
        }
    private val label = targets.label().let { key ->
        val signature = MethodType.fromMethodDescriptorString(key.descriptor(), loader)
        Class.forName(key.owner().replace('/', '.'), false, loader).getDeclaredMethod(key.name(), *signature.parameterArray())
            .also { check(it.trySetAccessible()) { "播放器菜单控件不可访问" } }
    }
    private var handle: AutoCloseable? = null

    @Suppress("UNCHECKED_CAST")
    fun install() {
        val popup = targets.popup()
        handle = WorkshopApi.hookRegistrar.appendContent(popup.owner().replace('/', '.'), popup.name(),
            targets.caller().replace('/', '.'), "content", scope as Class<Any>,
            parameterTypes = listOf("kotlin.jvm.functions.Function0", "kotlin.jvm.functions.Function2", "kotlin.jvm.functions.Function1"),
            priority = 50, content = ContentHook<Any> { builder, call ->
                val dismiss = call.argument<() -> Unit>(0)
                for ((title, page) in listOf("手动搜索歌词" to LyricsToolPage.SEARCH, "编辑元数据" to LyricsToolPage.METADATA)) {
                    val open: () -> Unit = { dismiss(); workspace.open(page) }
                    val content: @Composable () -> Unit = {
                        val icon: @Composable () -> Unit = {
                            DesktopIcon(if (page == LyricsToolPage.SEARCH) DesktopGlyph.Search else DesktopGlyph.Lyrics,
                                if (isDark()) Color.White else Color.Black, Modifier.size(16.dp))
                        }
                        label.invoke(null, title, Modifier, icon, true, currentComposer, 0, 0)
                    }
                    item.invoke(builder, open, true, content)
                }
            })
    }

    override fun close() { handle?.close(); handle = null }
}
