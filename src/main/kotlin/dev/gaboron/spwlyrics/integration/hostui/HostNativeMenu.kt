@file:OptIn(androidx.compose.runtime.InternalComposeApi::class)
package dev.gaboron.spwlyrics.integration.hostui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.integration.composeui.DesktopGlyph
import dev.gaboron.spwlyrics.integration.composeui.DesktopIcon
import dev.gaboron.spwlyrics.integration.workspace.LyricsToolPage
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import java.lang.reflect.Method

/** The host owns popup placement, animation, material, typography and menu-item interaction. */
internal class HostNativeMenu(
    scope: Class<*>,
    private val label: Method,
    private val popup: Method?,
    private val workspace: LyricsWorkspace,
    private val isDark: @Composable () -> Boolean,
) {
    private val item = scope.methods.single { method -> method.parameterTypes.map { it.name } ==
        listOf("kotlin.jvm.functions.Function0", "boolean", "kotlin.jvm.functions.Function2") }.also {
        check(it.trySetAccessible()) { "宿主菜单构建器不可访问" }
    }

    @Composable
    fun Popup(dismiss: () -> Unit, actions: List<Pair<String, LyricsToolPage>>) {
        val builder: (Any) -> Unit = { menu -> append(menu, actions, dismiss) }
        // Match the caption menu's call: no optional leading content; native defaults stay in charge.
        checkNotNull(popup).invoke(null, dismiss, null, builder, currentComposer, 0, 2)
    }

    fun append(menu: Any, actions: List<Pair<String, LyricsToolPage>>, dismiss: () -> Unit) {
        for ((title, page) in actions) {
            val open: () -> Unit = {
                dismiss()
                workspace.open(page)
            }
            val content: @Composable () -> Unit = {
                val icon: @Composable () -> Unit = {
                    val glyph = when (page) {
                        LyricsToolPage.SEARCH -> DesktopGlyph.Search
                        LyricsToolPage.METADATA -> DesktopGlyph.Lyrics
                        LyricsToolPage.BATCH -> DesktopGlyph.Library
                    }
                    DesktopIcon(glyph, if (isDark()) Color.White else Color.Black, Modifier.size(16.dp))
                }
                label.invoke(null, title, Modifier, icon, true, currentComposer, 0, 0)
            }
            item.invoke(menu, open, true, content)
        }
    }
}
