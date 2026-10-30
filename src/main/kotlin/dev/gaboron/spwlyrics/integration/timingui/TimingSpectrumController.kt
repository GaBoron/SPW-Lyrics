package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.application.TimingAudioDecoder
import dev.gaboron.spwlyrics.domain.TimingSpectrum
import dev.gaboron.spwlyrics.codec.TimingSpectrumAnalysis
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The visible window moves immediately; decoding is debounced and caches neighbouring audio. */
internal class TimingSpectrumController(private val decoder: TimingAudioDecoder) : AutoCloseable {
    var data by mutableStateOf<TimingSpectrum?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var dependency by mutableStateOf(decoder.dependency()); private set
    var spanMs by mutableLongStateOf(16000); private set
    var viewStartMs by mutableLongStateOf(0); private set
    private var path: Path? = null
    private var durationMs = 0L
    private var requestedStart = 0L
    private var requestedSpan = 0L
    private val generation = AtomicLong()
    private val worker = ScheduledThreadPoolExecutor(1) { Thread(it, "spw-lyrics-spectrum").apply { isDaemon = true } }
        .apply { removeOnCancelPolicy = true }
    private var task: Future<*>? = null
    @Volatile private var closed = false

    fun refreshDependency() {
        val next = decoder.dependency()
        if (next.available && !dependency.available) error = null
        dependency = next
    }
    fun follow(file: Path, centerMs: Long, duration: Long, force: Boolean = false) {
        val changed = path != file
        if (changed) reset()
        path = file; durationMs = duration
        if (!force && !changed && error != null) return
        val margin = minOf(1000L, spanMs / 8)
        val end = viewStartMs + spanMs
        if (!force && !changed && centerMs >= viewStartMs + (if (viewStartMs == 0L) 0 else margin) &&
            centerMs <= end - (if (duration > 0 && end >= duration) 0 else margin)) {
            ensureData(); return
        }
        viewStartMs = clamp(centerMs - spanMs / 2)
        ensureData(force = force)
    }
    fun zoom(inward: Boolean, centerMs: Long) {
        spanMs = (if (inward) spanMs / 2 else spanMs * 2).coerceIn(2000, 60000)
        viewStartMs = clamp(centerMs - spanMs / 2)
        ensureData(force = true)
    }
    fun panBy(deltaMs: Long) {
        val next = clamp(viewStartMs + deltaMs)
        if (next == viewStartMs) return
        viewStartMs = next
        ensureData(debounce = 70)
    }
    fun retry() { refreshDependency(); ensureData(force = true) }
    private fun clamp(start: Long) = start.coerceIn(0, if (durationMs > 0) (durationMs - spanMs).coerceAtLeast(0) else Long.MAX_VALUE / 2)
    private fun ensureData(force: Boolean = false, debounce: Long = 0) {
        val file = path ?: return
        if (closed) return
        val end = if (durationMs > 0) minOf(viewStartMs + spanMs, durationMs) else viewStartMs + spanMs
        if (!force && data?.let { viewStartMs >= it.startMs && end <= it.endMs } == true) {
            if (loading && (viewStartMs < requestedStart || end > requestedStart + requestedSpan)) cancel()
            return
        }
        if (!force && loading && viewStartMs >= requestedStart && end <= requestedStart + requestedSpan) return
        cancel()
        val revision = generation.get()
        requestedSpan = (spanMs * 3).coerceAtMost(60000)
        requestedStart = (viewStartMs - spanMs).coerceIn(0,
            if (durationMs > 0) (durationMs - requestedSpan).coerceAtLeast(0) else Long.MAX_VALUE / 2)
        val start = requestedStart; val length = requestedSpan
        loading = true; error = null
        task = worker.schedule({
            val result = runCatching {
                val cancelled = { closed || generation.get() != revision || Thread.currentThread().isInterrupted }
                TimingSpectrumAnalysis.analyze(decoder.decode(file, start, length, cancelled), cancelled)
            }
            EventQueue.invokeLater {
                if (!closed && generation.get() == revision) {
                    loading = false
                    result.onSuccess {
                        data = it
                        if (durationMs == 0L && it.durationMs < length) durationMs = it.endMs
                    }.onFailure { error = it.message ?: "频谱分析未完成，请重试。" }
                }
            }
        }, debounce, TimeUnit.MILLISECONDS)
    }
    fun cancel() { generation.incrementAndGet(); task?.cancel(true); task = null; decoder.cancel(); loading = false; requestedSpan = 0 }
    fun reset() { cancel(); path = null; data = null; error = null; viewStartMs = 0 }
    override fun close() { closed = true; cancel(); worker.shutdownNow(); decoder.close() }
}
