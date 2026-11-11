package dev.gaboron.spwlyrics.integration.tutorialui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Fade page ink, leaving transparent pixels untouched so native acrylic has no rectangular scrim. */
internal fun Modifier.tutorialContentFocus(target: Rect?, active: Boolean): Modifier = drawWithContent {
    if (!active) {
        drawContent()
        return@drawWithContent
    }
    val path = tutorialSpotlightPath(target)
    clipPath(path, ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = .44f })
            this@drawWithContent.drawContent()
            canvas.restore()
        }
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}

private fun DrawScope.tutorialSpotlightPath(target: Rect?): Path = Path().apply {
    target?.inflate(4.dp.toPx())?.intersect(Rect(Offset.Zero, size))?.takeUnless { it.isEmpty }?.let {
        addRoundRect(RoundRect(it, CornerRadius(8.dp.toPx())))
    }
}

internal fun DrawScope.drawTutorialSpotlight(target: Rect?, color: Color) {
    drawPath(tutorialSpotlightPath(target), color, style = Stroke(2.dp.toPx()))
}

/** One outline lets the host material, border and shadow cover both the bubble and its pointer. */
internal class TutorialBubbleShape(private val corners: Shape, private val target: Rect?) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val inset = minOf(12.dp.toPx(), size.minDimension / 4)
        val card = Rect(inset, inset, size.width - inset, size.height - inset)
        val body = Path().apply {
            addOutline(corners.createOutline(card.size, layoutDirection, density))
            translate(card.topLeft)
        }
        if (target == null || target.overlaps(card)) return Outline.Generic(body)
        val half = 8.dp.toPx()
        val margin = minOf(20.dp.toPx(), card.size.minDimension / 2)
        val pointer = Path().apply {
            if (target.right <= card.left || target.left >= card.right) {
                val left = target.right <= card.left
                val x = if (left) card.left else card.right
                val y = target.center.y.coerceIn(card.top + margin, card.bottom - margin)
                moveTo(x, y - half)
                lineTo(if (left) 0f else size.width, y)
                lineTo(x, y + half)
            } else {
                val above = target.center.y < card.center.y
                val y = if (above) card.top else card.bottom
                val x = target.center.x.coerceIn(card.left + margin, card.right - margin)
                moveTo(x - half, y)
                lineTo(x, if (above) 0f else size.height)
                lineTo(x + half, y)
            }
            close()
        }
        Outline.Generic(Path.combine(PathOperation.Union, body, pointer))
    }
}

/** Prefer a side bubble; narrow panes use the larger space above or below the highlighted control. */
internal fun tutorialBubbleOffset(target: Rect, viewport: IntSize, bubble: IntSize, gap: Float): Offset {
    val width = viewport.width.toFloat()
    val height = viewport.height.toFloat()
    val padding = gap * 3
    fun x(value: Float) = value.coerceIn(padding, (width - bubble.width - padding).coerceAtLeast(padding))
    fun y(value: Float) = value.coerceIn(padding, (height - bubble.height - padding).coerceAtLeast(padding))
    return when {
        width - target.right >= bubble.width + gap -> Offset(x(target.right + gap), y(target.center.y - bubble.height / 2))
        target.left >= bubble.width + gap -> Offset(x(target.left - bubble.width - gap), y(target.center.y - bubble.height / 2))
        height - target.bottom >= target.top -> Offset(x(target.center.x - bubble.width / 2), y(target.bottom + gap))
        else -> Offset(x(target.center.x - bubble.width / 2), y(target.top - bubble.height - gap))
    }
}
