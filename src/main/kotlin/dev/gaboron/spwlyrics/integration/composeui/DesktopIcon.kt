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

internal enum class DesktopGlyph { Search, Refresh, Chevron, Check, Library, Lyrics, Back, Exit, Edit, Drag }

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
            DesktopGlyph.Back -> { line(14f, 5f, 7f, 12f); line(7f, 12f, 14f, 19f) }
            DesktopGlyph.Exit -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            DesktopGlyph.Edit -> {
                line(5f, 15f, 15f, 5f); line(15f, 5f, 19f, 9f); line(19f, 9f, 9f, 19f)
                line(9f, 19f, 4f, 20f); line(4f, 20f, 5f, 15f); line(13f, 7f, 17f, 11f)
            }
            DesktopGlyph.Drag -> {
                for (x in listOf(9f, 15f)) for (y in listOf(6f, 12f, 18f)) drawCircle(color, stroke, point(x, y))
            }
            DesktopGlyph.Lyrics -> {
                drawRoundRect(color, point(3f, 4f), androidx.compose.ui.geometry.Size(size.width * 18/24, size.height * 16/24),
                    androidx.compose.ui.geometry.CornerRadius(size.width / 12), style = Stroke(stroke))
                line(6f, 8f, 12f, 8f); line(6f, 12f, 10f, 12f); line(6f, 16f, 10f, 16f)
                line(17f, 8f, 17f, 15f); line(17f, 8f, 19f, 9f)
                drawOval(color, point(13f, 14f), androidx.compose.ui.geometry.Size(size.width * 4/24, size.height * 3/24))
            }
            DesktopGlyph.Refresh -> {
                drawArc(color, 45f, 285f, false, point(4f, 4f), androidx.compose.ui.geometry.Size(size.width * 16/24, size.height * 16/24), style = Stroke(stroke))
                line(20f, 3f, 20f, 9f); line(20f, 9f, 14f, 9f)
            }
            DesktopGlyph.Library -> {
                line(4f, 5f, 4f, 20f); line(9f, 5f, 9f, 20f); line(14f, 5f, 18f, 20f); line(3f, 20f, 21f, 20f)
            }
        }
    }
}
