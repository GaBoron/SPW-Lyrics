package dev.gaboron.spwlyrics.provider

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

interface ProviderHttp {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String
    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
    fun postForm(url: String, values: Map<String, String>, headers: Map<String, String> = emptyMap()): String
}

class ProviderHttpClient(
    connectTimeout: Duration = Duration.ofSeconds(2),
    private val requestTimeout: Duration = Duration.ofSeconds(3),
) : ProviderHttp {
    private val connectTimeoutMs = connectTimeout.toTimeoutMillis()
    private val readTimeoutMs = requestTimeout.toTimeoutMillis()
    private val directHttpsFallback = DirectHttpsFallback(connectTimeoutMs, readTimeoutMs)

    override fun get(url: String, headers: Map<String, String>): String = try {
        send(url, "GET", null, null, headers)
    } catch (failure: Throwable) {
        if (!url.startsWith("https://") || !failure.hasTlsFailure()) throw failure
        runCatching { directHttpsFallback.get(url, headers) }.getOrElse { fallbackFailure ->
            failure.addSuppressed(fallbackFailure)
            throw failure
        }
    }

    override fun postJson(url: String, body: String, headers: Map<String, String>): String = send(
        url,
        "POST",
        "application/json; charset=utf-8",
        body.toByteArray(StandardCharsets.UTF_8),
        headers,
    )

    override fun postForm(url: String, values: Map<String, String>, headers: Map<String, String>): String {
        val body = values.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return send(
            url,
            "POST",
            "application/x-www-form-urlencoded; charset=utf-8",
            body.toByteArray(StandardCharsets.UTF_8),
            headers,
        )
    }

    private fun send(
        url: String,
        method: String,
        contentType: String?,
        body: ByteArray?,
        headers: Map<String, String>,
    ): String {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            check(status in 200..299) { "HTTP $status" }
            val rawStream = connection.inputStream
            val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }
            return stream.bufferedReader(responseCharset(connection.contentType)).use { it.readText() }
        } finally {
            connection.errorStream?.close()
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 SPW-Lyrics/0.2.0"
        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        private fun Duration.toTimeoutMillis(): Int = toMillis().coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

        private fun responseCharset(contentType: String?) = contentType
            ?.let { Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?.let { runCatching { java.nio.charset.Charset.forName(it.trim('"')) }.getOrNull() }
            ?: StandardCharsets.UTF_8
    }
}

private fun Throwable.hasTlsFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is SSLException }
