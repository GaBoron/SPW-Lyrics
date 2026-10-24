package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.codec.LyricEditTime
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo

internal data class SpectralWord(val cursor: TimingCursor, val text: String, val start: Long, val end: Long)

/** The remembered bitmap and its disposable lifetime match; playback redraws only the overlays. */
@Composable
internal fun TimingSpectrumImage(data: TimingSpectrum, words: List<SpectralWord>, selected: TimingCursor?,
    playing: Set<TimingCursor>, positionMs: Long?, seek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { spectrumBitmap(data) }
    val image = remember(bitmap) { bitmap.asComposeImageBitmap() }
    DisposableEffect(bitmap) { onDispose { bitmap.close() } }
    val accent = SaltTheme.colors.highlight
    val secondary = SaltTheme.colors.subText
    val selectedStart = words.firstOrNull { it.cursor == selected }?.start
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { i -> Text(LyricEditTime.format(data.startMs + data.durationMs * i / 4),
                style = SaltTheme.textStyles.sub.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), maxLines = 1) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(SaltTheme.shapes.small)
            .pointerInput(data) { detectTapGestures { seek(data.startMs + (it.x / size.width * data.durationMs).toLong()) } }) {
            Image(image, "频谱：20 Hz 到 8 kHz，对数频率", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                fun x(time: Long) = (time - data.startMs).toFloat() / data.durationMs * size.width
                repeat(5) { i -> drawLine(Color.White.copy(alpha = .10f), Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height)) }
                words.forEach { word ->
                    val left = x(word.start).coerceIn(0f, size.width)
                    val right = x(word.end).coerceIn(0f, size.width)
                    val active = word.cursor == selected
                    val current = word.cursor in playing
                    if (active || current) drawRect(if (active) accent.copy(alpha = .25f) else Color.White.copy(alpha = .09f),
                        Offset(left, 0f), Size((right - left).coerceAtLeast(1f), size.height))
                    if (word.start in data.startMs..data.endMs) drawLine(if (active) accent else Color.White.copy(alpha = .22f),
                        Offset(left, 0f), Offset(left, size.height), if (active) 2.dp.toPx() else 1f)
                }
                selectedStart?.takeIf { it in data.startMs..data.endMs }?.let { drawLine(accent, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx()) }
                positionMs?.takeIf { it in data.startMs..data.endMs }?.let { drawLine(Color.White.copy(alpha = .75f), Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx()) }
            }
            words.filter { it.start >= data.startMs && it.start < data.endMs }.forEach { word ->
                val left = maxWidth * ((word.start - data.startMs).toFloat() / data.durationMs)
                val width = maxWidth * ((word.end - word.start).toFloat() / data.durationMs)
                if (width >= 20.dp) Text(word.text, Modifier.offset(x = left).widthIn(max = minOf(width, 150.dp)).padding(2.dp),
                    color = Color.White, style = SaltTheme.textStyles.sub, maxLines = 1)
            }
            Text("8k Hz", Modifier.align(Alignment.TopEnd).padding(3.dp), color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
            Text("20 Hz", Modifier.align(Alignment.BottomEnd).padding(3.dp), color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        }
        Canvas(Modifier.height(28.dp).fillMaxWidth().clip(SaltTheme.shapes.small)
            .pointerInput(data) { detectTapGestures { seek(data.startMs + (it.x / size.width * data.durationMs).toLong()) } }) {
            data.peaks.forEachIndexed { i, peak ->
                val x = size.width * i / data.peaks.size
                drawLine(secondary.copy(alpha = .5f), Offset(x, size.height * (.5f - peak * .48f)),
                    Offset(x, size.height * (.5f + peak * .48f)), (size.width / data.peaks.size).coerceAtLeast(1f))
            }
            positionMs?.takeIf { it in data.startMs..data.endMs }?.let {
                val x = (it - data.startMs).toFloat() / data.durationMs * size.width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height))
            }
        }
    }
}

private fun spectrumBitmap(data: TimingSpectrum): Bitmap {
    val bytes = ByteArray(data.columns * data.bands * 4)
    val colors = intArrayOf(0x080D24, 0x244ECC, 0x22E2D0, 0xEFFFF9)
    data.levels.forEachIndexed { i, level ->
        val value = (level.toInt() and 255) / 255f * 3
        val stop = value.toInt().coerceAtMost(2)
        val fraction = value - stop
        val first = colors[stop]; val second = colors[stop + 1]
        repeat(3) { channel ->
            val shift = channel * 8
            val a = first shr shift and 255; val b = second shr shift and 255
            bytes[i * 4 + channel] = (a + (b - a) * fraction).toInt().toByte()
        }
        bytes[i * 4 + 3] = 255.toByte()
    }
    return Bitmap().apply { check(installPixels(ImageInfo.makeN32Premul(data.columns, data.bands), bytes, data.columns * 4)) }
}
