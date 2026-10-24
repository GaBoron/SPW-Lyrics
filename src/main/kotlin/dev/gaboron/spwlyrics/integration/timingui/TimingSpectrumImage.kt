package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.codec.LyricEditTime
import kotlin.math.roundToInt

internal data class SpectralWord(val cursor: TimingCursor, val text: String, val start: Long, val end: Long, val lane: Int = 0)

/** Persistent gesture/word layers; changing PCM replaces only the remembered raster. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TimingSpectrumImage(data: TimingSpectrum, viewStart: Long, span: Long, words: List<SpectralWord>, selected: TimingCursor?,
    playing: Set<TimingCursor>, positionMs: Long?, duration: Long, seek: (Long) -> Unit, edit: (SpectralWord) -> Unit,
    pan: (Long) -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { spectrumBitmap(data) }
    val image = remember(bitmap) { bitmap.asComposeImageBitmap() }
    DisposableEffect(bitmap) { onDispose { bitmap.close() } }
    val accent = SaltTheme.colors.highlight
    val secondary = SaltTheme.colors.subText
    val animatedStart by animateFloatAsState(viewStart.toFloat(), tween(70), label = "spectrum scroll")
    val start = animatedStart.toLong()
    var width by remember { mutableIntStateOf(1) }
    var preview by remember { mutableStateOf<SpectralWord?>(null) }
    val shown = preview?.let { draft -> words.map { if (it.cursor == draft.cursor) draft else it } } ?: words
    val primary = preview?.cursor ?: selected
    Column(modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }.onPointerEvent(PointerEventType.Scroll) { event ->
        if (preview == null) {
            val delta = event.changes.sumOf { (if (it.scrollDelta.x != 0f) it.scrollDelta.x else it.scrollDelta.y).toDouble() }
            if (delta != 0.0) { pan((span * delta * 30 / width).toLong()); event.changes.forEach { it.consume() } }
        }
    }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { i -> Text(LyricEditTime.format(start + span * i / 4),
                style = SaltTheme.textStyles.sub.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), maxLines = 1) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(SaltTheme.shapes.small).background(Color(0xFF080D24))) {
            val lanes = (words.maxOfOrNull { it.lane + 1 } ?: 1).coerceAtLeast(1)
            val laneHeight = minOf(23.dp, maxHeight * .4f / lanes)
            val lanePixels = with(androidx.compose.ui.platform.LocalDensity.current) { laneHeight.toPx() }
            val input = Modifier.fillMaxSize().spectrumWordInput(start, span, words, selected, lanePixels, duration,
                { preview = it }, edit, seek)
            Canvas(input.semantics { contentDescription = "频谱：20 Hz 到 8 kHz，对数频率；滚轮横移，拖动字块或边界改时间" }) {
                fun x(time: Long) = (time - start).toFloat() / span * size.width
                val left = x(data.startMs)
                drawImage(image, dstOffset = IntOffset(left.roundToInt(), 0),
                    dstSize = IntSize((data.durationMs.toDouble() / span * size.width).roundToInt().coerceAtLeast(1), size.height.roundToInt()))
                repeat(5) { i -> drawLine(Color.White.copy(alpha = .10f), Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height)) }
                shown.forEach { word ->
                    val from = x(word.start).coerceIn(0f, size.width)
                    val to = x(word.end).coerceIn(0f, size.width)
                    val active = word.cursor == primary
                    if (active || word.cursor in playing) drawRect(if (active) accent.copy(alpha = .25f) else Color.White.copy(alpha = .09f),
                        Offset(from, 0f), Size((to - from).coerceAtLeast(1f), size.height))
                    drawRect(if (active) accent.copy(alpha = .65f) else Color.White.copy(alpha = .13f),
                        Offset(from, lanePixels * word.lane), Size((to - from).coerceAtLeast(1f), lanePixels))
                    if (word.start in start..start + span) drawLine(if (active) accent else Color.White.copy(alpha = .28f),
                        Offset(from, lanePixels * word.lane), Offset(from, size.height), if (active) 2.dp.toPx() else 1f)
                    if (active && word.end in start..start + span) drawLine(accent, Offset(to, lanePixels * word.lane), Offset(to, size.height), 2.dp.toPx())
                }
                positionMs?.takeIf { it in start..start + span }?.let { drawLine(Color.White.copy(alpha = .75f), Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx()) }
            }
            shown.filter { it.start < start + span && it.end > start }.forEach { word ->
                val from = ((word.start - start).toFloat() / span).coerceAtLeast(0f)
                val to = ((word.end - start).toFloat() / span).coerceAtMost(1f)
                val cellWidth = maxWidth * (to - from)
                if (cellWidth >= 16.dp) Text(word.text,
                    Modifier.offset(x = maxWidth * from, y = laneHeight * word.lane).widthIn(max = minOf(cellWidth, 150.dp)).padding(2.dp),
                    color = Color.White, style = SaltTheme.textStyles.sub, maxLines = 1)
            }
            Text("8k Hz", Modifier.align(Alignment.TopEnd).padding(3.dp), color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
            Text("20 Hz", Modifier.align(Alignment.BottomEnd).padding(3.dp), color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        }
        Canvas(Modifier.height(28.dp).fillMaxWidth().clip(SaltTheme.shapes.small)
            .pointerInput(start, span) { detectTapGestures { seek((start + (it.x / size.width * span).toLong()).coerceAtLeast(0)) } }) {
            data.peaks.forEachIndexed { i, peak ->
                val time = data.startMs + data.durationMs * i / data.peaks.size
                if (time in start..start + span) {
                    val x = (time - start).toFloat() / span * size.width
                    drawLine(secondary.copy(alpha = .5f), Offset(x, size.height * (.5f - peak * .48f)),
                        Offset(x, size.height * (.5f + peak * .48f)), (size.width * data.durationMs / span / data.peaks.size).coerceAtLeast(1f))
                }
            }
            positionMs?.takeIf { it in start..start + span }?.let {
                val x = (it - start).toFloat() / span * size.width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height))
            }
        }
    }
}
