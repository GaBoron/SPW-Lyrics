package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.application.TimingPlayback
import dev.gaboron.spwlyrics.application.TimingPlaybackSnapshot
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/** Public playback commands plus the exact clock and speed used by the host's lyric renderer. */
internal class SpwTimingPlayback : TimingPlayback {
    override fun pauseForCalibration() { WorkshopApi.playback.pause() }
    private data class Access(val controller: Any, val service: Any, val position: Method, val playing: Method,
        val rate: Method, val changeRate: Method)
    private var access: Access? = null
    private var checkedPaths: Pair<Path, String>? = null
    private val seekGeneration = AtomicLong()

    fun seeked() { seekGeneration.incrementAndGet() }

    override fun read(expectedPath: Path): TimingPlaybackSnapshot {
        val item = matching(expectedPath)
        val generation = seekGeneration.get()
        val prepared = access ?: discover().also { access = it }
        val position = (prepared.position.invoke(null, prepared.service) as Number).toLong()
        val playing = prepared.playing.invoke(null, prepared.service) as Boolean
        val rate = (prepared.rate.invoke(prepared.controller) as Number).toFloat()
        val after = matching(expectedPath)
        check(item.id == after.id && item.path == after.path) { "宿主已切歌，请重新播放正在编辑的文件。" }
        check(generation == seekGeneration.get()) { "宿主正在跳转，请稍后重试。" }
        check(position >= 0 && rate.isFinite() && rate > 0) { "宿主播放时钟暂不可用。" }
        return TimingPlaybackSnapshot(item.id, item.path, position, item.duration, playing, rate, generation)
    }

    override fun play(expectedPath: Path) { matching(expectedPath); WorkshopApi.playback.play() }
    override fun pause(expectedPath: Path) { matching(expectedPath); WorkshopApi.playback.pause() }
    override fun seek(expectedPath: Path, positionMs: Long) {
        val item = matching(expectedPath)
        WorkshopApi.playback.seekTo(positionMs.coerceIn(0, item.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }
    override fun rate(expectedPath: Path, value: Float) {
        matching(expectedPath)
        require(value.isFinite() && value in .5f..2f) { "打轴倍速范围为 0.5× 到 2×。" }
        restoreRate(value)
    }

    override fun restoreRate(value: Float) {
        require(value.isFinite() && value > 0)
        val prepared = access ?: discover().also { access = it }
        prepared.changeRate.invoke(prepared.controller, value)
    }

    private fun matching(expectedPath: Path): com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem {
        val item = WorkshopApi.playback.getCurrentMediaItem() ?: error("请先在 SPW 播放正在编辑的歌曲。")
        val pair = expectedPath to item.path
        if (checkedPaths != pair) {
            check(runCatching { Files.isSameFile(expectedPath, Path.of(item.path)) }.getOrDefault(false)) {
                "当前播放歌曲与编辑文件不同，请在 SPW 播放该文件后打轴。"
            }
            checkedPaths = pair
        }
        return item
    }

    private fun discover(): Access = try {
        val type = Class.forName("com.xuncorp.voxzen.service.PlaybackController")
        val controller = type.getField("INSTANCE").get(null)
        val serviceField = type.declaredFields.single { Modifier.isStatic(it.modifiers) && it.type.name == "com.xuncorp.voxzen.service.PlaybackService" }
        check(serviceField.trySetAccessible())
        val service = checkNotNull(serviceField.get(null))
        // These host-owned suppliers are the clock and playing-state sources wired into its lyrics emitter.
        fun supplier(result: Class<*>): Method = service.javaClass.declaredMethods.single { method ->
            Modifier.isStatic(method.modifiers) && method.name.startsWith("lyricsEmitter\$lambda\$") &&
                method.returnType == result && method.parameterTypes.contentEquals(arrayOf(service.javaClass))
        }.also { check(it.trySetAccessible()) }
        Access(controller, service, supplier(Long::class.javaPrimitiveType!!), supplier(Boolean::class.javaPrimitiveType!!),
            type.getMethod("getPlaybackSpeed"), type.getMethod("changeSpeed", Float::class.javaPrimitiveType))
    } catch (error: Exception) {
        throw IllegalStateException("当前 SPW 版本的打轴时钟或倍速接口不可用，请更新宿主或反馈版本信息。", error)
    }
}
