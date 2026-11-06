@file:OptIn(androidx.compose.runtime.InternalComposeApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon
import dev.gaboron.spwlyrics.integration.composeui.DesktopTheme
import dev.gaboron.spwlyrics.integration.composeui.DesktopPopupMenu
import dev.gaboron.spwlyrics.integration.composeui.LocalDesktopPopupMaterial
import com.moriafly.salt.ui.popup.PopupMenuItem
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import dev.gaboron.spwlyrics.integration.workspace.LyricsToolPage
import java.lang.invoke.MethodType
import java.lang.reflect.Method

/** Only this boundary translates discovered JVM members to host objects and Compose calls. */
internal class HostUiBridge(val targets: HostUiDiscovery.Targets, private val loader: ClassLoader,
    private val workspace: LyricsWorkspace, popup: HostPopupMaterialDiscovery.Targets?) {
    private val caption = method(targets.captionControl())
    private val page = method(targets.page().target())
    private val themeConfigs = method(targets.theme().configs())
    private val themeDark = method(targets.theme().dark())
    private val navigate = method(targets.navigate())
    private val collapsePlayer = method(targets.collapsePlayer())
    private val playerState = field(targets.playerState())
    private val playerExpanded = method(targets.playerExpanded())
    private val hostBack = method(targets.back())
    private val stack = field(targets.backStack())
    private val navigatorType = stack.declaringClass
    private val locals = targets.locals().map(::field)
    private val popupModifier = popup?.modifier()?.let(::method)
    private val popupMaterial = popup?.material()?.let(::field)?.get(null)
    private val popupMenu = popup?.menu()?.let(::method)
    private var captionMenu: HostNativeMenu? = null
    val popupSurface: (@Composable (Modifier) -> Modifier)? = popupModifier?.let { reflected ->
        { modifier -> reflected.invoke(null, modifier, popupMaterial, currentComposer, 0) as Modifier }
    }
    var replaying = false
        private set
    private var navigator: Any? = null

    fun prepareCaptionMenu(targets: HostPlayerMenuDiscovery.Targets) {
        captionMenu = HostNativeMenu(Class.forName(targets.scope().replace('/', '.'), false, loader),
            method(targets.label()), checkNotNull(popupMenu) { "宿主顶部弹出菜单不可用" }, workspace, ::isDarkTheme)
    }

    fun method(key: HostBytecodeIndex.Method): Method {
        val signature = MethodType.fromMethodDescriptorString(key.descriptor(), loader)
        return Class.forName(key.owner().replace('/', '.'), false, loader)
            .getDeclaredMethod(key.name(), *signature.parameterArray()).also {
                check(it.returnType == signature.returnType() && it.trySetAccessible()) { "宿主 UI 方法不可访问" }
            }
    }

    private fun field(key: HostBytecodeIndex.Field) = Class.forName(key.owner().replace('/', '.'), false, loader)
        .getDeclaredField(key.name()).also {
            check(it.type.descriptorString() == key.descriptor() && it.trySetAccessible()) { "宿主 UI 状态不可访问或类型已改变" }
        }

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
        replay {
            collapsePlayer.invoke(host)
            navigate.invoke(host, current)
        }
    }

    fun replay(action: () -> Unit) {
        replaying = true
        try { action() } finally { replaying = false }
    }

    fun playerExpanded(): Boolean = navigator?.let { playerExpanded.invoke(playerState.get(it)) as Boolean } == true

    fun back() {
        if (playerExpanded()) replay { hostBack.invoke(navigator) }
        else workspace.requestLeave {}
    }

    @Composable
    fun isDarkTheme(): Boolean = themeDark.invoke(themeConfigs.invoke(null, currentComposer)) as Boolean

    @Composable
    fun HostPage() {
        page.invoke(null, currentComposer, 0)
    }

    @Composable
    @OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
    fun Entry() {
        var expanded by remember { mutableStateOf(false) }
        val dark = isDarkTheme()
        val icon: @Composable BoxScope.() -> Unit = {
            DesktopIcon(DesktopGlyph.Lyrics, if (dark) Color.White else Color.Black,
                Modifier.align(Alignment.Center).size(20.dp))
        }
        Box {
            val openMenu: () -> Unit = { expanded = true }
            caption.invoke(null, openMenu, "歌词管理", Modifier, true, null, 0f, icon, currentComposer, 0, 32)
            val actions = listOf("手动搜索歌词" to LyricsToolPage.SEARCH,
                "编辑元数据" to LyricsToolPage.METADATA, "批量管理" to LyricsToolPage.BATCH)
            val native = captionMenu
            if (native != null) {
                if (expanded) native.Popup({ expanded = false }, actions)
            } else {
                // Keep a working entry when a host update makes native popup discovery unavailable.
                DesktopTheme(dark) {
                    CompositionLocalProvider(LocalDesktopPopupMaterial provides popupSurface) {
                        DesktopPopupMenu(expanded, { expanded = false }) {
                            for ((title, target) in actions) {
                                PopupMenuItem(onClick = {
                                    expanded = false
                                    workspace.open(target)
                                }, text = title)
                            }
                        }
                    }
                }
            }
        }
    }
}
