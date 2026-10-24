package dev.gaboron.spwlyrics.codec

import java.util.Locale
import kotlin.math.roundToLong

internal object LyricEditTime {
    fun parse(value: String): Long? {
        val text = value.trim().removeSurrounding("[", "]")
        if (text.isBlank()) return null
        val result = when {
            text.endsWith("ms") -> text.dropLast(2).toDoubleOrNull()
            text.endsWith('s') -> text.dropLast(1).toDoubleOrNull()?.times(1000)
            text.endsWith('m') -> text.dropLast(1).toDoubleOrNull()?.times(60000)
            text.endsWith('h') -> text.dropLast(1).toDoubleOrNull()?.times(3600000)
            text.contains(':') -> {
                val parts = text.split(':')
                require(parts.size in 2..3) { "时间请填写 mm:ss.mmm 或 hh:mm:ss.mmm。" }
                val numbers = parts.map { it.toDoubleOrNull() ?: error("时间格式无效：$value") }
                require(numbers.drop(1).all { it >= 0 && it < 60 }) { "时间的秒、分字段必须小于 60。" }
                if (parts.size == 3) (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]) * 1000
                else (numbers[0] * 60 + numbers[1]) * 1000
            }
            else -> text.toDoubleOrNull() // Bare values are milliseconds in the editor.
        }
        require(result != null && result.isFinite() && result >= 0 && result <= Long.MAX_VALUE / 2.0) { "时间必须是非负数：$value" }
        return result.roundToLong()
    }

    fun format(time: Long?): String = time?.let { String.format(Locale.ROOT, "%02d:%02d.%03d", it / 60000, it / 1000 % 60, it % 1000) }.orEmpty()
    fun shift(value: String, delta: Long): String = parse(value)?.let { format(Math.addExact(it, delta).coerceAtLeast(0)) }.orEmpty()
}
