@file:OptIn(androidx.compose.runtime.InternalComposeApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.lang.invoke.MethodType
import java.lang.reflect.Method

/** Only this boundary translates discovered JVM members to host objects and Compose calls. */
internal class HostUiBridge(val targets: HostUiDiscovery.Targets, private val loader: ClassLoader,
    private val workspace: LyricsWorkspace) {
    private val caption = method(targets.captionControl())
    private val page = method(targets.page().target())
    private val navigate = method(targets.navigate())
    private val stack = field(targets.backStack())
    private val navigatorType = stack.declaringClass
    private val locals = targets.locals().map(::field)
    var replaying = false
        private set
    private var navigator: Any? = null
    private val openPage: () -> Unit = { workspace.open() }

    fun method(key: HostBytecodeIndex.Method): Method {
        val signature = MethodType.fromMethodDescriptorString(key.descriptor(), loader)
        return Class.forName(key.owner().replace('/', '.'), false, loader)
            .getDeclaredMethod(key.name(), *signature.parameterArray()).also {
                check(it.returnType == signature.returnType() && it.trySetAccessible()) { "宿主 UI 方法不可访问" }
            }
    }

    private fun field(key: HostBytecodeIndex.Field) = Class.forName(key.owner().replace('/', '.'), false, loader)
        .getDeclaredField(key.name()).also { check(it.trySetAccessible()) { "宿主 UI 状态不可访问" } }

    fun capture(composer: Composer) {
        for (field in locals) {
            @Suppress("UNCHECKED_CAST")
            val local = field.get(null) as CompositionLocal<Any?>
            val value = composer.consume(local)
            if (navigatorType.isInstance(value)) { navigator = value; return }
        }
        error("未取得宿主导航上下文")
    }

    fun enter() {
        val host = checkNotNull(navigator) { "主窗口尚未准备好，请稍后重试" }
        val current = (stack.get(host) as List<*>).lastOrNull() ?: return
        // A same-route request closes other host-composed tools before the host's early return.
        replay { navigate.invoke(host, current) }
    }

    fun replay(action: () -> Unit) {
        replaying = true
        try { action() } finally { replaying = false }
    }

    @Composable
    fun HostPage() {
        page.invoke(null, currentComposer, 0)
    }

    @Composable
    fun Entry() {
        val icon: @Composable BoxScope.() -> Unit = {
            DesktopIcon(DesktopGlyph.Lyrics, if (isSystemInDarkTheme()) Color.White else Color.Black,
                Modifier.align(Alignment.Center).size(18.dp))
        }
        caption.invoke(null, openPage, "歌词管理", Modifier, true, null, 0f, icon, currentComposer, 0, 32)
    }
}
