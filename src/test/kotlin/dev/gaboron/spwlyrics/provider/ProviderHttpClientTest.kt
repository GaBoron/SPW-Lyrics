package dev.gaboron.spwlyrics.provider

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProviderHttpClientTest {
    @Test
    fun `does not link the optional java net http module`() {
        val resource = ProviderHttpClient::class.java.getResourceAsStream("ProviderHttpClient.class")
            ?: error("class resource missing")
        val constantPool = resource.use { it.readAllBytes().toString(StandardCharsets.ISO_8859_1) }

        assertFalse(constantPool.contains("java/net/http"))
    }

    @Test
    fun `reads gzip response through java base networking`() {
        val expected = "网络层可用"
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(expected.toByteArray(StandardCharsets.UTF_8)) }
        }.toByteArray()
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/plain; charset=utf-8\r\n")
                        append("Content-Encoding: gzip\r\n")
                        append("Content-Length: ${compressed.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(StandardCharsets.US_ASCII)
                    socket.getOutputStream().apply { write(header); write(compressed); flush() }
                }
            }
            val client = ProviderHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1))

            assertEquals(expected, client.get("http://127.0.0.1:${server.localPort}/lyrics"))
            worker.join(1_000)
        }
    }
}
