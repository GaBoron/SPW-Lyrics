@file:OptIn(androidx.compose.runtime.InternalComposeApi::class, com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.hook.MethodHook
import com.xuncorp.spw.workshop.api.hook.MethodHookParam
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon
import dev.gaboron.spwlyrics.integration.workspace.LyricsToolPage
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.lang.invoke.MethodType

/** Appends native player actions through the host's DSL; the original builder still runs first. */
internal class HostPlayerMenu(private val targets: HostPlayerMenuDiscovery.Targets, private val loader: ClassLoader,
    private val workspace: LyricsWorkspace, private val isDark: @Composable () -> Boolean) : AutoCloseable {
    private val scope = Class.forName(targets.scope().replace('/', '.'), false, loader)
    private val item = scope.methods.single { method -> method.parameterTypes.map { it.name } ==
        listOf("kotlin.jvm.functions.Function0", "boolean", "kotlin.jvm.functions.Function2") }.also {
            check(it.trySetAccessible()) { "播放器菜单构建器不可访问" }
        }
    private fun method(key: HostBytecodeIndex.Method): java.lang.reflect.Method {
        val signature = MethodType.fromMethodDescriptorString(key.descriptor(), loader)
        return Class.forName(key.owner().replace('/', '.'), false, loader).getDeclaredMethod(key.name(), *signature.parameterArray())
            .also { check(it.returnType == signature.returnType() && it.trySetAccessible()) { "播放器菜单控件不可访问或类型已改变" } }
    }
    private val builder = method(targets.builder())
    private val dismiss = method(targets.dismiss())
    private val label = method(targets.label())
    private var handle: AutoCloseable? = null

    init {
        val parameters = builder.parameterTypes
        check(targets.scopeParameter() in parameters.indices && targets.popupParameter() in parameters.indices &&
            targets.scopeParameter() != targets.popupParameter() && parameters[targets.scopeParameter()] == scope &&
            parameters[targets.popupParameter()] == dismiss.declaringClass) { "播放器菜单参数已改变" }
    }

    fun install() {
        handle = WorkshopApi.hookRegistrar.hookMethod(builder.declaringClass.name, builder.name,
            builder.parameterTypes.map { it.name }, object : MethodHook(50) {
                override fun after(call: MethodHookParam) {
                    if (call.throwable != null) return
                    val menuScope = call.args[targets.scopeParameter()] ?: return
                    val popup = call.args[targets.popupParameter()] ?: return
                    appendItems(menuScope) { dismiss.invoke(popup) }
                }
            })
    }

    private fun appendItems(menuScope: Any, closePopup: () -> Unit) {
        for ((title, page) in listOf("手动搜索歌词" to LyricsToolPage.SEARCH, "编辑元数据" to LyricsToolPage.METADATA)) {
            val open: () -> Unit = { closePopup(); workspace.open(page) }
            val content: @Composable () -> Unit = {
                val icon: @Composable () -> Unit = {
                    DesktopIcon(if (page == LyricsToolPage.SEARCH) DesktopGlyph.Search else DesktopGlyph.Lyrics,
                        if (isDark()) Color.White else Color.Black, Modifier.size(16.dp))
                }
                label.invoke(null, title, Modifier, icon, true, currentComposer, 0, 0)
            }
            item.invoke(menuScope, open, true, content)
        }
    }

    override fun close() { handle?.close(); handle = null }
}
