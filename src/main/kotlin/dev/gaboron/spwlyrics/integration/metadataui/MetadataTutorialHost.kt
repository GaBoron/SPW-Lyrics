package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import kotlinx.coroutines.delay

private data class TutorialAnchor(val owner: Any, val bounds: Rect)
private class TutorialAnchors(val step: MetadataTutorialStep) {
    val positions = mutableStateMapOf<MetadataTutorialTarget, TutorialAnchor>()
}
private val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchors?> { null }

/** Register real, clipped layout bounds; scroll the requested control into view after pane transitions. */
@Composable
internal fun Modifier.metadataTutorialTarget(target: MetadataTutorialTarget): Modifier {
    val anchors = LocalTutorialAnchors.current ?: return this
    val owner = remember { Any() }
    val bring = remember { BringIntoViewRequester() }
    DisposableEffect(anchors, target) {
        onDispose { if (anchors.positions[target]?.owner === owner) anchors.positions.remove(target) }
    }
    LaunchedEffect(anchors, target) {
        if (anchors.step.target == target) {
            delay(350)
            bring.bringIntoView()
        }
    }
    return bringIntoViewRequester(bring).onGloballyPositioned {
        anchors.positions[target] = TutorialAnchor(owner, it.boundsInRoot())
    }
}

@Composable
internal fun MetadataTutorialHost(controller: MetadataTutorialController, available: Boolean,
    content: @Composable (MetadataTutorialStep?, MetadataTutorialDemo?) -> Unit) {
    val step = controller.step.takeIf { controller.active && available }
    val anchors = remember(step) { step?.let(::TutorialAnchors) }
    val demo = remember(step != null) { if (step != null) MetadataTutorialDemo() else null }
    DisposableEffect(demo) { onDispose { demo?.close() } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        CompositionLocalProvider(LocalTutorialAnchors provides anchors) { content(step, demo) }
        if (step != null && anchors != null) {
            val bounds = anchors.positions[step.target]?.bounds?.takeUnless(Rect::isEmpty)
                ?: anchors.positions[MetadataTutorialTarget.LYRICS]?.bounds
            val target = bounds?.translate(-origin)
            // Keep the overlay in the editor's viewport, even when the short-window page itself scrolls.
            val position = remember { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize) = anchorBounds.topLeft
            } }
            Popup(position, properties = PopupProperties(focusable = true, dismissOnBackPress = false,
                dismissOnClickOutside = false)) {
                MetadataTutorialOverlay(controller, target, Modifier.size(maxWidth, maxHeight))
            }
        }
    }
}

@Composable
private fun MetadataTutorialOverlay(controller: MetadataTutorialController, target: Rect?, modifier: Modifier) {
    val accent = SaltTheme.colors.highlight
    val shape = SaltTheme.shapes.large
    val surface = SaltTheme.colors.popup
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val scroll = rememberScrollState()
    LaunchedEffect(controller.index) {
        scroll.scrollTo(0)
        withFrameNanos { }
        focus.requestFocus()
    }
    BoxWithConstraints(modifier.focusRequester(focus).onPreviewKeyEvent {
        when {
            it.type != KeyEventType.KeyDown -> false
            it.key == Key.Escape -> {
                controller.finish()
                true
            }
            it.key == Key.DirectionRight || it.key == Key.Enter -> {
                controller.next()
                true
            }
            it.key == Key.DirectionLeft -> {
                controller.previous()
                true
            }
            // Never forward editor or timing shortcuts through the focusable tutorial popup.
            it.isCtrlPressed && it.key != Key.Tab -> true
            else -> false
        }
    }.focusable()) {
        val bubbleOffset = tutorialBubbleOffset(target, IntSize(constraints.maxWidth, constraints.maxHeight), bubbleSize,
            with(density) { 16.dp.toPx() })
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            val viewport = Rect(Offset.Zero, size)
            val hole = target?.inflate(4.dp.toPx())?.intersect(viewport)?.takeUnless { it.isEmpty }
            val shade = Color.Black.copy(alpha = .56f)
            if (hole == null) drawRect(shade)
            else {
                drawRect(shade, size = Size(size.width, hole.top))
                drawRect(shade, Offset(0f, hole.bottom), Size(size.width, size.height - hole.bottom))
                drawRect(shade, Offset(0f, hole.top), Size(hole.left, hole.height))
                drawRect(shade, Offset(hole.right, hole.top), Size(size.width - hole.right, hole.height))
                drawRoundRect(accent, hole.topLeft, hole.size, CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
                if (bubbleSize != IntSize.Zero) {
                    val card = Rect(bubbleOffset, Size(bubbleSize.width.toFloat(), bubbleSize.height.toFloat()))
                    val half = 8.dp.toPx()
                    val pointer = Path().apply {
                        if (card.left >= hole.right || card.right <= hole.left) {
                            val right = card.left >= hole.right
                            val x = if (right) card.left else card.right
                            val y = hole.center.y.coerceIn(card.top + 20.dp.toPx(), card.bottom - 20.dp.toPx())
                            moveTo(x, y - half)
                            lineTo(if (right) hole.right else hole.left, hole.center.y)
                            lineTo(x, y + half)
                        } else {
                            val below = card.center.y >= hole.center.y
                            val y = if (below) card.top else card.bottom
                            val x = hole.center.x.coerceIn(card.left + 20.dp.toPx(), card.right - 20.dp.toPx())
                            moveTo(x - half, y)
                            lineTo(hole.center.x, if (below) hole.bottom else hole.top)
                            lineTo(x + half, y)
                        }
                        close()
                    }
                    drawPath(pointer, surface)
                    drawPath(pointer, accent.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
                }
            }
        }
        Column(Modifier.offset { IntOffset(bubbleOffset.x.toInt(), bubbleOffset.y.toInt()) }
            .width(minOf(440.dp, maxWidth - 24.dp)).heightIn(max = maxHeight * .72f).onSizeChanged { bubbleSize = it }
            .shadow(12.dp, shape).background(SaltTheme.colors.popup, shape)
            .border(1.dp, accent.copy(alpha = .35f), shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("虚拟歌曲演示", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                Text("${controller.index + 1} / ${MetadataTutorialStep.entries.size}", color = accent)
            }
            LinearProgressIndicator((controller.index + 1f) / MetadataTutorialStep.entries.size,
                Modifier.fillMaxWidth().height(3.dp), color = accent, backgroundColor = accent.copy(alpha = .15f))
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)
                .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(controller.step.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(controller.step.detail)
                controller.error?.let { Text(it, color = SaltTheme.colors.error) }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopButton("跳过教程", controller::finish)
                DesktopButton("上一步", controller::previous, enabled = controller.index > 0)
                DesktopButton(if (controller.index == MetadataTutorialStep.entries.lastIndex) "完成教程" else "下一步",
                    controller::next, primary = true)
            }
            Text("← / → 切换，Enter 下一步，Esc 跳过", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
        }
    }
}

/** Prefer a side bubble; narrow panes use the larger space above or below the highlighted control. */
private fun tutorialBubbleOffset(target: Rect?, viewport: IntSize, bubble: IntSize, gap: Float): Offset {
    val width = viewport.width.toFloat()
    val height = viewport.height.toFloat()
    fun x(value: Float) = value.coerceIn(12f, (width - bubble.width - 12f).coerceAtLeast(12f))
    fun y(value: Float) = value.coerceIn(12f, (height - bubble.height - 12f).coerceAtLeast(12f))
    if (target == null) return Offset(x((width - bubble.width) / 2), y((height - bubble.height) / 2))
    return when {
        width - target.right >= bubble.width + gap -> Offset(x(target.right + gap), y(target.center.y - bubble.height / 2))
        target.left >= bubble.width + gap -> Offset(x(target.left - bubble.width - gap), y(target.center.y - bubble.height / 2))
        height - target.bottom >= target.top -> Offset(x(target.center.x - bubble.width / 2), y(target.bottom + gap))
        else -> Offset(x(target.center.x - bubble.width / 2), y(target.top - bubble.height - gap))
    }
}
