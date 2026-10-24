package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.application.TimingAudioDecoder
import dev.gaboron.spwlyrics.domain.TimingSpectrum
import dev.gaboron.spwlyrics.codec.TimingSpectrumAnalysis
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

internal class TimingSpectrumController(private val decoder: TimingAudioDecoder) : AutoCloseable {
    var data by mutableStateOf<TimingSpectrum?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var spanMs by mutableLongStateOf(16000); private set
    private var path: Path? = null
    private var requestedStart = 0L
    private var durationMs = 0L
    private var loadedSpanMs = 0L
    private val generation = AtomicLong()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "spw-lyrics-spectrum").apply { isDaemon = true } }
    private var task: Future<*>? = null
    private var closed = false

    fun follow(file: Path, centerMs: Long, duration: Long, force: Boolean = false) {
        val margin = minOf(1000L, spanMs / 8)
        if (!force && path == file && error != null) return
        val maximum = if (duration > 0) (duration - spanMs).coerceAtLeast(0) else Long.MAX_VALUE
        val start = (centerMs - spanMs / 2).coerceIn(0, maximum)
        if (!force && path == file && (loading || data != null && loadedSpanMs == spanMs)) {
            val from = if (loading) requestedStart else data!!.startMs
            val end = if (loading) from + spanMs else data!!.endMs
            val rightMargin = if (duration > 0 && end >= duration || !loading && data!!.durationMs < loadedSpanMs) 0 else margin
            if (centerMs >= from + (if (from == 0L) 0 else margin) && centerMs <= end - rightMargin) return
        }
        load(file, start, duration)
    }

    fun zoom(inward: Boolean, centerMs: Long) {
        spanMs = (if (inward) spanMs / 2 else spanMs * 2).coerceIn(2000, 60000)
        path?.let { follow(it, centerMs, durationMs, force = true) }
    }
    fun pan(direction: Int) { path?.let { load(it, (requestedStart + direction * spanMs / 2).coerceIn(0,
        if (durationMs > 0) (durationMs - spanMs).coerceAtLeast(0) else Long.MAX_VALUE), durationMs) } }
    fun retry() { path?.let { load(it, requestedStart, durationMs) } }

    private fun load(file: Path, start: Long, duration: Long) {
        if (closed) return
        cancel()
        val revision = generation.get()
        if (path != file) data = null
        path = file; requestedStart = start; durationMs = duration
        loading = true; error = null
        val span = spanMs
        task = worker.submit {
            val result = runCatching {
                val cancelled = { closed || generation.get() != revision || Thread.currentThread().isInterrupted }
                TimingSpectrumAnalysis.analyze(decoder.decode(file, start, span, cancelled), cancelled)
            }
            EventQueue.invokeLater {
                if (!closed && generation.get() == revision) {
                    loading = false
                    result.onSuccess { data = it; loadedSpanMs = span }.onFailure { error = it.message ?: "频谱分析未完成，请重试。" }
                }
            }
        }
    }
    fun cancel() { generation.incrementAndGet(); task?.cancel(true); task = null; decoder.cancel(); loading = false }
    fun reset() { cancel(); path = null; data = null; error = null; loadedSpanMs = 0 }
    override fun close() { closed = true; cancel(); worker.shutdownNow(); decoder.close() }
}
