package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.TimingAudioDecoder
import dev.gaboron.spwlyrics.domain.TimingPcm
import dev.gaboron.spwmedia.sdk.MediaRuntimeException
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Analysis only: lease the Runtime's FFmpeg and decode a bounded PCM window without audio output. */
internal class SpwTimingAudioDecoder(private val runtime: TimingMediaRuntime, private val install: TimingRuntimeInstall) : TimingAudioDecoder {
    override fun dependency() = install.dependency()
    private val process = AtomicReference<Process?>()
    @Volatile private var closed = false
    private val watchdog = Executors.newSingleThreadScheduledExecutor { Thread(it, "spw-lyrics-decode-timeout").apply { isDaemon = true } }
    override fun decode(path: Path, startMs: Long, durationMs: Long, cancelled: () -> Boolean): TimingPcm {
        require(startMs >= 0 && durationMs in 1000..60000)
        check(!closed)
        try {
            val (client, generation) = runtime.client(cancelled)
            client.tools.acquire("ffmpeg").use { tool ->
                if (cancelled()) throw InterruptedException()
                val decoder = runtime.start(client, generation, listOf(tool.path.toString(), "-nostdin", "-v", "error",
                    "-ss", (startMs / 1000.0).toString(), "-i", path.toString(), "-t", (durationMs / 1000.0).toString(),
                    "-map", "0:a:0", "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-f", "s16le", "pipe:1"))
                process.set(decoder)
                val timedOut = AtomicBoolean()
                var deadline: java.util.concurrent.ScheduledFuture<*>? = null
                try {
                    if (closed || cancelled()) throw InterruptedException()
                    deadline = watchdog.schedule({ timedOut.set(true); decoder.destroyForcibly() }, 25, TimeUnit.SECONDS)
                    val limit = (durationMs * 16 * 2).toInt()
                    val output = ByteArrayOutputStream(limit)
                    val chunk = ByteArray(8192)
                    decoder.inputStream.use { input ->
                        while (true) {
                            if (closed || cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = input.read(chunk)
                            if (count < 0) break
                            check(output.size() + count <= limit + 32000) { "音频分析数据超过当前时间窗。" }
                            output.write(chunk, 0, count)
                        }
                    }
                    check(decoder.waitFor(5, TimeUnit.SECONDS) && decoder.exitValue() == 0 && !timedOut.get()) {
                        if (timedOut.get()) "音频分析超时，请缩小时间窗后重试。" else "音频解码失败，请检查文件是否可读及 Runtime 是否正常。"
                    }
                    val bytes = output.toByteArray()
                    val samples = FloatArray(bytes.size / 2) { i ->
                        ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort() / 32768f
                    }
                    runtime.verify(client, generation)
                    return TimingPcm(startMs, 16000, samples)
                } finally {
                    deadline?.cancel(false)
                    process.compareAndSet(decoder, null)
                    // The executable lease must outlive its process, including cancellation.
                    if (decoder.isAlive) decoder.destroyForcibly()
                    var interrupted = false
                    while (decoder.isAlive) try { decoder.waitFor() } catch (_: InterruptedException) { interrupted = true }
                    runtime.finished(decoder)
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        } catch (error: MediaRuntimeException) {
            throw IllegalStateException("Runtime 音频分析不可用（${error.detail.code}），请确认依赖已启用后重新分析。", error)
        }
    }
    override fun cancel() { process.get()?.destroyForcibly() }
    override fun close() { closed = true; cancel(); watchdog.shutdownNow() }
}
