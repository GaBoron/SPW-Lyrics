package dev.gaboron.spwlyrics.integration.composeui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class DesktopGlyph { Search, Refresh, Chevron, Music, Check, Library }

@Composable
internal fun DesktopIcon(glyph: DesktopGlyph, color: Color, modifier: Modifier = Modifier.size(15.dp)) {
    Canvas(modifier) {
        fun point(x: Float, y: Float) = Offset(size.width * x / 24, size.height * y / 24)
        val stroke = size.width / 14
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, point(x, y), point(xx, yy), stroke, StrokeCap.Round)
        when (glyph) {
            DesktopGlyph.Search -> { drawCircle(color, size.width * .28f, point(10f, 10f), style = Stroke(stroke)); line(15f, 15f, 21f, 21f) }
            DesktopGlyph.Chevron -> { line(6f, 9f, 12f, 15f); line(12f, 15f, 18f, 9f) }
            DesktopGlyph.Check -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 6f) }
            DesktopGlyph.Refresh -> {
                drawArc(color, 45f, 285f, false, point(4f, 4f), androidx.compose.ui.geometry.Size(size.width * 16/24, size.height * 16/24), style = Stroke(stroke))
                line(20f, 3f, 20f, 9f); line(20f, 9f, 14f, 9f)
            }
            DesktopGlyph.Music -> {
                line(9f, 17f, 9f, 5f); line(9f, 5f, 20f, 3f); line(20f, 3f, 20f, 15f)
                drawOval(color, point(3f, 16f), androidx.compose.ui.geometry.Size(size.width * 6/24, size.height * 5/24))
                drawOval(color, point(14f, 14f), androidx.compose.ui.geometry.Size(size.width * 6/24, size.height * 5/24))
            }
            DesktopGlyph.Library -> {
                line(4f, 5f, 4f, 20f); line(9f, 5f, 9f, 20f); line(14f, 5f, 18f, 20f); line(3f, 20f, 21f, 20f)
            }
        }
    }
}
