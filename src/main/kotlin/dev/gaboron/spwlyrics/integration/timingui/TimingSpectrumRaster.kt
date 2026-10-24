package dev.gaboron.spwlyrics.integration.timingui

import dev.gaboron.spwlyrics.domain.TimingSpectrum
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo

internal fun spectrumBitmap(data: TimingSpectrum): Bitmap {
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
