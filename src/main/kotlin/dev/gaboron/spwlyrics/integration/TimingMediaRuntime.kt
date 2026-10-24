package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwmedia.sdk.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

/** Plugin-owned SDK connection. Only its own analysis processes are stopped on disconnect. */
internal class TimingMediaRuntime(private val pluginId: String) : AutoCloseable {
    private val lock = Any()
    private var connection: RuntimeConnection? = null
    private val processes = mutableSetOf<Process>()
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
                    try { stopProcesses(previous); CompletableFuture.completedFuture(null) }
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

    fun start(client: MediaRuntime, generation: Long, command: List<String>): Process = synchronized(lock) {
        check(!closed && accepting && retired !== client && connection?.current === client && connection?.snapshot?.generation == generation) { "Runtime 已重连，请重新分析。" }
        ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start().also(processes::add)
    }
    fun finished(process: Process) { synchronized(lock) { processes.remove(process) } }
    fun verify(client: MediaRuntime, generation: Long) {
        client.capabilities() // Validate the old token even before the next periodic health check.
        synchronized(lock) { check(connection?.current === client && connection?.snapshot?.generation == generation) { "Runtime 已重连，请重新分析。" } }
    }
    private fun stopProcesses(previous: MediaRuntime? = null) {
        val owned = synchronized(lock) { accepting = false; if (previous != null) retired = previous; processes.toList() }
        owned.forEach { process ->
            process.destroy()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                check(process.waitFor(2, TimeUnit.SECONDS)) { "音频分析进程尚未退出，保留 Runtime 租约。" }
            }
        }
    }
    override fun close() {
        val owner = synchronized(lock) { closed = true; connection }
        stopProcesses()
        owner?.close()
    }
}
