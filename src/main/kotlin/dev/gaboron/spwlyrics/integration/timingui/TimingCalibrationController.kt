package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.application.*
import dev.gaboron.spwlyrics.domain.TimingJudge
import dev.gaboron.spwlyrics.domain.InputLatencyCalibration
import dev.gaboron.spwlyrics.storage.TimingCalibrationStore
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

internal class TimingCalibrationController(private val store: TimingCalibrationStore, private val createAudio: () -> CalibrationBeatAudio) : AutoCloseable {
    var saved by mutableStateOf(store.read()); private set
    var candidate by mutableStateOf<InputLatencyCalibration?>(null); private set
    var running by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var count by mutableIntStateOf(0); private set
    var positionMs by mutableDoubleStateOf(0.0); private set
    var lastOffset by mutableStateOf<Double?>(null); private set
    var message by mutableStateOf("先听 4 拍，再跟随鼓点按空格或 G。至少记录 12 次后可保存。"); private set
    var error by mutableStateOf(false); private set
    var bpm by mutableIntStateOf(120); private set
    private var model = TimingLatencyCalibration()
    @Volatile private var audio: CalibrationBeatAudio? = null
    private var down: Double? = null
    private var judge = TimingJudge.DOWN
    private val generation = AtomicLong()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "spw-lyrics-calibration").apply { isDaemon = true } }
    private var task: Future<*>? = null
    private var closed = false

    fun bpm(value: Int) { if (!running && value in 60..180) bpm = value }
    fun start(mode: TimingJudge) {
        if (closed || saving) return
        stop()
        model = TimingLatencyCalibration(bpm); judge = mode
        val revision = generation.get()
        count = 0; positionMs = 0.0; lastOffset = null; candidate = null; error = false; running = true
        message = "跟随鼓点敲击；前 4 拍仅用于准备。"
        task = worker.submit {
            val result = runCatching {
                createAudio().use { output ->
                    audio = output
                    if (closed || generation.get() != revision) return@use
                    output.play(bpm, TimingLatencyCalibration.WARMUP + TimingLatencyCalibration.SAMPLES + 2) { closed || generation.get() != revision || Thread.currentThread().isInterrupted }
                }
            }
            val device = audio?.device.orEmpty()
            audio = null
            EventQueue.invokeLater {
                if (!closed && generation.get() == revision) {
                    running = false; down = null
                    candidate = model.result(device)
                    result.onFailure { error = true; message = "鼓点播放失败：${it.message ?: "请检查系统音频输出"}" }
                    if (result.isSuccess) message = if (candidate != null) "测量完成，可保存结果用于下次打轴。" else "有效敲击不足或波动较大，请重新测量。"
                }
            }
        }
    }
    fun poll() { store.read().let { if (saved != it) saved = it }; if (running) audio?.positionMs()?.let { positionMs = it } }
    fun press() {
        if (!running || down != null) return
        down = audio?.positionMs() ?: return
        if (judge == TimingJudge.DOWN) record(down!!)
    }
    fun release() {
        val from = down ?: return
        down = null
        if (judge == TimingJudge.DOWN) return
        val to = audio?.positionMs() ?: return
        record(if (judge == TimingJudge.UP) to else (from + to) / 2)
    }
    fun click() { if (down == null) press(); release() }
    fun cancelPress() { down = null }
    private fun record(time: Double) {
        model.tap(time)?.let { lastOffset = it; count = model.offsets.size }
        candidate = model.result(audio?.device.orEmpty())
    }
    fun save(value: InputLatencyCalibration? = candidate) {
        if (closed || saving || value == null || value.delayMs !in -500..500) return
        stop(); saving = true; error = false
        task = worker.submit {
            val result = runCatching { store.write(value) }
            EventQueue.invokeLater {
                if (!closed) {
                    saving = false
                    result.onSuccess { saved = value; message = "已保存输入延迟 ${value.delayMs} ms，下次打轴自动使用。" }
                        .onFailure { error = true; message = "保存补偿失败：${it.message ?: "请检查配置目录"}" }
                }
            }
        }
    }
    fun stop() {
        generation.incrementAndGet()
        if (!saving) { task?.cancel(true); task = null }
        audio?.close(); audio = null; down = null; running = false
    }
    override fun close() { closed = true; stop(); worker.shutdown() }
}
