package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Icons, loading indicators, and their spacing enter and leave as one animated slot. */
@Composable
internal fun DesktopButtonIndicator(loading: Boolean, glyph: DesktopGlyph?, color: Color) {
    AnimatedContent(loading to glyph, label = "button indicator",
        contentKey = { if (it.first) "loading" else it.second },
        transitionSpec = {
            (fadeIn(tween(150)) togetherWith fadeOut(tween(100))).using(
                SizeTransform(sizeAnimationSpec = { _, _ -> tween(160) })
            )
        }) { (busy, icon) ->
        if (busy || icon != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = color, strokeWidth = 1.5.dp)
            else if (icon != null) DesktopIcon(icon, color)
            Spacer(Modifier.width(7.dp))
        }
    }
}
