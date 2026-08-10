package dev.gaboron.spwlyrics.integration.manualui

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

class ManualUiBridge(
    pluginRoot: Path,
    private val session: ManualUiSession,
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val token = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers: ExecutorService = Executors.newCachedThreadPool { task ->
        Thread(task, "spw-lyrics-manual-ui").apply { isDaemon = true }
    }
    private val executable = pluginRoot.resolve("ui").resolve("SpwLyrics.WinUI.exe")

    init {
        workers.execute {
            while (!server.isClosed) {
                runCatching { server.accept() }.getOrNull()?.let { socket -> workers.execute { handle(socket) } }
            }
        }
    }

    fun open(): Boolean {
        if (!Files.isRegularFile(executable)) return false
        val process = runCatching {
            ProcessBuilder(
                executable.toString(),
                "--port", server.localPort.toString(),
                "--token", token,
            ).directory(executable.parent.toFile()).redirectErrorStream(true).start()
        }.getOrNull() ?: return false
        workers.execute { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { } } }
        if (runCatching { process.waitFor(750, TimeUnit.MILLISECONDS) }.getOrDefault(false)) return false
        return true
    }

    private fun handle(socket: Socket) = socket.use { client ->
        client.soTimeout = 20_000
        val line = client.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
        val response = if (line == null || line.length > MAX_REQUEST_LENGTH) {
            ManualUiResponse(false, "请求无效。")
        } else {
            val request = runCatching { json.decodeFromString<ManualUiRequest>(line) }.getOrNull()
            when {
                request == null -> ManualUiResponse(false, "请求格式无效。")
                !constantTimeEquals(request.token, token) -> ManualUiResponse(false, "连接认证失败。")
                else -> runCatching { session.handle(request) }
                    .getOrElse { ManualUiResponse(false, it.message ?: "操作失败。") }
            }
        }
        client.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(response))
            writer.newLine()
        }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var difference = 0
        left.indices.forEach { difference = difference or (left[it].code xor right[it].code) }
        return difference == 0
    }

    override fun close() {
        runCatching { server.close() }
        workers.shutdownNow()
    }

    companion object {
        private const val MAX_REQUEST_LENGTH = 1_048_576
    }
}
