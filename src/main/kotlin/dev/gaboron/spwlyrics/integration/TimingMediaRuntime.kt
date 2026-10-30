package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwmedia.sdk.*
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

/** Plugin-owned SDK connection. Only its own analysis processes are stopped on disconnect. */
internal class TimingMediaRuntime(private val pluginId: String, private val performance: PluginPerformance) : AutoCloseable {
    private val lock = Any()
    private var connection: RuntimeConnection? = null
    private val processes = mutableMapOf<Process, AnalysisProcess>()
    private var accepting = true
    private var retired: MediaRuntime? = null
    private var closed = false

    fun client(cancelled: () -> Boolean): Pair<MediaRuntime, Long> {
        check(MediaRuntime.isInstalled()) { "频谱编辑需要同时安装并启用 SPW Media Runtime；启用后点击重新分析。" }
        val owner = synchronized(lock) {
            check(!closed)
            connection ?: MediaRuntime.connectWhenAvailable(pluginId,
                ConnectionOptions(retryTimeoutMillis = 5000, retryIntervalMillis = 500, healthCheckIntervalMillis = 1000),
                Function { previous ->
                    try { stopProcesses(previous) }
                    catch (error: Exception) { CompletableFuture.failedFuture(error) }
                }).also { connection = it }
        }
        if (owner.snapshot.status == ConnectionStatus.UNAVAILABLE) owner.reconnect()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7)
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException()
            owner.current?.let { client ->
                val capability = client.capabilities().firstOrNull { it.id == "processing" }
                check(capability?.available == true) { "Runtime 音频解码不可用：${capability?.reason ?: "未提供 processing 能力"}" }
                synchronized(lock) { check(!closed && retired !== client) { "Runtime 正在重连，请重新分析。" }; accepting = true }
                return client to owner.snapshot.generation
            }
            check(owner.snapshot.status != ConnectionStatus.UNAVAILABLE && System.nanoTime() < deadline) {
                "Runtime 连接未就绪，请确认已启用 SPW Media Runtime 后重试。"
            }
            Thread.sleep(25)
        }
    }

    /** A successful start transfers lease ownership; the caller closes it if start fails. */
    fun start(client: MediaRuntime, generation: Long, command: List<String>, lease: AutoCloseable): Process = synchronized(lock) {
        check(!closed && accepting && retired !== client && connection?.current === client && connection?.snapshot?.generation == generation) { "Runtime 已重连，请重新分析。" }
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val owned = AnalysisProcess(process, lease)
        processes[process] = owned
        owned.released.whenComplete { _, error ->
            synchronized(lock) { processes.remove(process) }
            if (error != null) System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "释放音频分析租约失败", error)
        }
        process
    }
    fun finished(process: Process) { synchronized(lock) { processes[process] }?.stop() }
    fun verify(client: MediaRuntime, generation: Long) {
        client.capabilities() // Validate the old token even before the next periodic health check.
        synchronized(lock) { check(!closed && connection?.current === client && connection?.snapshot?.generation == generation) { "Runtime 已重连，请重新分析。" } }
    }
    private fun stopProcesses(previous: MediaRuntime? = null): CompletableFuture<Void> {
        val owned = synchronized(lock) {
            accepting = false
            if (previous != null) retired = previous
            processes.values.toList()
        }
        owned.forEach(AnalysisProcess::stop)
        return CompletableFuture.allOf(*owned.map { it.released }.toTypedArray())
    }
    override fun close() {
        val owner = synchronized(lock) {
            if (closed) return
            closed = true
            connection
        }
        stopProcesses()
        Thread.ofVirtual().name("spw-lyrics-media-cleanup").start {
            performance.measure(PluginPerformance.Operation.MEDIA_CLEANUP) {
                runCatching { owner?.close() }.onFailure {
                    // SDK cleanup timeout abandons executable pins instead of releasing live tools.
                    System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "Runtime 清理未完成，已保留进程资源保护", it)
                }
            }
            performance.report("media cleanup")
        }
    }
}
