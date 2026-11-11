package dev.gaboron.spwlyrics.integration.tutorialui

import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.gaboron.spwlyrics.integration.composeui.LocalDesktopPopupMaterial
import kotlinx.coroutines.delay

private data class TutorialAnchor(val owner: Any, val bounds: Rect)
private class TutorialAnchors {
    val positions = mutableStateMapOf<TutorialTarget, TutorialAnchor>()
}
private data class TutorialContext(val anchors: TutorialAnchors, val step: TutorialStep)
private val LocalTutorialContext = staticCompositionLocalOf<TutorialContext?> { null }

/** Register real, clipped layout bounds; scroll the requested control into view after pane transitions. */
@Composable
internal fun Modifier.tutorialTarget(target: TutorialTarget): Modifier {
    val context = LocalTutorialContext.current ?: return this
    val anchors = context.anchors
    val owner = remember { Any() }
    val bring = remember { BringIntoViewRequester() }
    DisposableEffect(anchors, target) {
        onDispose { if (anchors.positions[target]?.owner === owner) anchors.positions.remove(target) }
    }
    LaunchedEffect(context.step, target) {
        if (context.step.target == target) {
            delay(350)
            bring.bringIntoView()
        }
    }
    return bringIntoViewRequester(bring).onGloballyPositioned {
        anchors.positions[target] = TutorialAnchor(owner, it.boundsInRoot())
    }
}

@Composable
internal fun <S : TutorialStep, D : AutoCloseable> TutorialHost(controller: TutorialController<S>, available: Boolean, createDemo: () -> D,
    content: @Composable (S?, D?) -> Unit) {
    val step = controller.step.takeIf { controller.active && available }
    val demo = remember(step != null) { if (step != null) createDemo() else null }
    val anchors = remember(demo) { TutorialAnchors() }
    var retainedTarget by remember(demo) { mutableStateOf<Rect?>(null) }
    DisposableEffect(demo) { onDispose { demo?.close() } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val measuredTarget = step?.let { anchors.positions[it.target]?.bounds }?.translate(-origin)?.takeUnless { it.isEmpty }
    // A tab can briefly have no measured target. Retain its predecessor instead of centering the bubble.
    SideEffect { if (measuredTarget != null) retainedTarget = measuredTarget }
    val placementTarget = retainedTarget
    val target = placementTarget?.let {
        val animated by animateRectAsState(it, spring(dampingRatio = 1f, stiffness = 420f), label = "tutorial spotlight")
        animated
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        Box(Modifier.fillMaxSize().tutorialContentFocus(target, step != null)) {
            CompositionLocalProvider(LocalTutorialContext provides step?.let { TutorialContext(anchors, it) }) { content(step, demo) }
        }
        if (step != null && target != null) {
            // Keep the overlay in the editor's viewport, even when the short-window page itself scrolls.
            val position = remember { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize) = anchorBounds.topLeft
            } }
            Popup(position, properties = PopupProperties(focusable = true, dismissOnBackPress = false,
                dismissOnClickOutside = false)) {
                TutorialOverlay(controller, target, placementTarget, Modifier.size(maxWidth, maxHeight))
            }
        }
    }
}

@Composable
private fun <S : TutorialStep> TutorialOverlay(controller: TutorialController<S>, target: Rect, placementTarget: Rect, modifier: Modifier) {
    val accent = SaltTheme.colors.highlight
    val corners = SaltTheme.shapes.large
    val material = LocalDesktopPopupMaterial.current
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val position = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var positioned by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val progress by animateFloatAsState((controller.index + 1f) / controller.steps.size,
        tween(300), label = "tutorial progress")
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
        // Bubble and spotlight share a destination, so their springs run together rather than in series.
        val destination = tutorialBubbleOffset(placementTarget, IntSize(constraints.maxWidth, constraints.maxHeight), bubbleSize,
            with(density) { 4.dp.toPx() })
        LaunchedEffect(destination, bubbleSize != IntSize.Zero) {
            if (bubbleSize == IntSize.Zero) return@LaunchedEffect
            if (!positioned) {
                position.snapTo(destination)
                positioned = true
            } else position.animateTo(destination, spring(dampingRatio = 1f, stiffness = 420f))
        }
        val bubbleOffset = position.value
        val shape = remember(corners, target, bubbleOffset) { TutorialBubbleShape(corners, target.translate(-bubbleOffset)) }
        val bounds = Modifier.clip(shape)
        val surface = material?.invoke(bounds) ?: bounds.background(SaltTheme.colors.popup)
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            drawTutorialSpotlight(target, accent)
        }
        Column(Modifier.offset { IntOffset(bubbleOffset.x.toInt(), bubbleOffset.y.toInt()) }
            .graphicsLayer { alpha = if (positioned) 1f else 0f }
            .onSizeChanged { bubbleSize = it }.shadow(12.dp, shape).then(surface)
            .border(1.dp, accent.copy(alpha = .35f), shape)
            .animateContentSize(spring(dampingRatio = 1f, stiffness = 420f), alignment = Alignment.TopStart)
            .width(minOf(464.dp, maxWidth - 24.dp)).heightIn(max = maxHeight * .8f).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("虚拟数据演示", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                TutorialPageText(controller.index, "tutorial counter") { index ->
                    Text("${index + 1} / ${controller.steps.size}", color = accent)
                }
            }
            LinearProgressIndicator(progress,
                Modifier.fillMaxWidth().height(3.dp), color = accent, backgroundColor = accent.copy(alpha = .15f))
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)
                .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TutorialPageText(controller.index, "tutorial explanation") { index ->
                    val step = controller.steps[index]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(step.detail)
                    }
                }
                controller.error?.let { Text(it, color = SaltTheme.colors.error) }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopButton("跳过教程", controller::finish)
                DesktopButton("上一步", controller::previous, enabled = controller.index > 0)
                DesktopButton(if (controller.index == controller.steps.lastIndex) "完成教程" else "下一步",
                    controller::next, primary = true)
            }
            Text("← / → 切换，Enter 下一步，Esc 跳过", color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
        }
    }
}

@Composable
private fun TutorialPageText(index: Int, label: String, content: @Composable (Int) -> Unit) {
    AnimatedContent(index, label = label, transitionSpec = {
        val direction = if (targetState > initialState) 1 else -1
        ((fadeIn(tween(180, 40)) + slideInVertically(tween(240)) { direction * it / 8 }) togetherWith
            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -direction * it / 8 })).using(
            SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(240) }))
    }, content = { content(it) })
}
