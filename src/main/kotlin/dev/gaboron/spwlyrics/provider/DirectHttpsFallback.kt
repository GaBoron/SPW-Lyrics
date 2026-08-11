package dev.gaboron.spwlyrics.provider

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Retries an HTTPS GET against a public DNS address while preserving the
 * original hostname for SNI and certificate verification.
 */
internal class DirectHttpsFallback(
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val resolver: PublicDnsResolver = PublicDnsResolver(),
) {
    fun get(url: String, headers: Map<String, String>): String = get(URI.create(url), headers, redirectsLeft = 3)

    private fun get(uri: URI, headers: Map<String, String>, redirectsLeft: Int): String {
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS required" }
        val host = uri.host ?: error("HTTPS host missing")
        val port = uri.port.takeIf { it >= 0 } ?: 443
        val addresses = resolver.resolve(host)
        check(addresses.isNotEmpty()) { "Public DNS returned no address for $host" }

        var lastFailure: Throwable? = null
        for (address in addresses) {
            try {
                val response = request(uri, host, port, address, headers)
                if (response.status in 300..399 && redirectsLeft > 0) {
                    val location = response.headers["location"] ?: error("HTTP ${response.status} without Location")
                    return get(uri.resolve(location), headers, redirectsLeft - 1)
                }
                check(response.status in 200..299) { "HTTP ${response.status}" }
                return response.text()
            } catch (failure: Throwable) {
                lastFailure = failure
            }
        }
        throw IllegalStateException("Direct HTTPS fallback failed for $host", lastFailure)
    }

    private fun request(
        uri: URI,
        host: String,
        port: Int,
        address: InetAddress,
        headers: Map<String, String>,
    ): Response {
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(address, port), connectTimeoutMs)
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, host, port, true) as SSLSocket
            return tls.use { socket ->
                socket.soTimeout = readTimeoutMs
                socket.sslParameters = socket.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(host))
                }
                socket.startHandshake()
                writeRequest(socket, uri, host, port, headers)
                readResponse(socket)
            }
        } catch (failure: Throwable) {
            runCatching { plain.close() }
            throw failure
        }
    }

    private fun writeRequest(socket: SSLSocket, uri: URI, host: String, port: Int, headers: Map<String, String>) {
        val target = (uri.rawPath?.takeIf(String::isNotEmpty) ?: "/") +
            uri.rawQuery?.let { "?$it" }.orEmpty()
        val authority = if (port == 443) host else "$host:$port"
        val requestHeaders = linkedMapOf(
            "Host" to authority,
            "User-Agent" to ProviderHttpClient.USER_AGENT,
            "Accept-Encoding" to "gzip",
            "Connection" to "close",
        ) + headers
        require(requestHeaders.all { (name, value) -> '\r' !in name && '\n' !in name && '\r' !in value && '\n' !in value })
        val request = buildString {
            append("GET ").append(target).append(" HTTP/1.1\r\n")
            requestHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        socket.outputStream.apply {
            write(request.toByteArray(StandardCharsets.US_ASCII))
            flush()
        }
    }

    private fun readResponse(socket: SSLSocket): Response {
        val input = BufferedInputStream(socket.inputStream)
        val statusLine = input.readAsciiLine() ?: error("Empty HTTPS response")
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: error("Invalid HTTP status")
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = input.readAsciiLine() ?: error("Incomplete HTTP headers")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> input.readChunked()
            headers["content-length"] != null -> input.readExactly(headers.getValue("content-length").toInt())
            else -> input.readAllBytes()
        }
        return Response(status, headers, body)
    }

    private data class Response(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
        fun text(): String {
            val decoded = if (headers["content-encoding"]?.contains("gzip", ignoreCase = true) == true) {
                GZIPInputStream(ByteArrayInputStream(body)).use { it.readAllBytes() }
            } else {
                body
            }
            return decoded.toString(responseCharset(headers["content-type"]))
        }
    }
}

internal class PublicDnsResolver(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun resolve(host: String): List<InetAddress> {
        cache[host]?.takeIf { it.expiresAt > nowMillis() }?.let { return it.addresses }
        val encoded = ProviderHttpClient.encode(host)
        val connection = URI.create("https://1.1.1.1/dns-query?name=$encoded&type=A")
            .toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = DNS_TIMEOUT_MS
        connection.readTimeout = DNS_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/dns-json")
        connection.setRequestProperty("User-Agent", ProviderHttpClient.USER_AGENT)
        val payload = try {
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val addresses = parsePublicIpv4Addresses(payload)
        if (addresses.isNotEmpty()) cache[host] = CacheEntry(addresses, nowMillis() + CACHE_TTL_MS)
        return addresses
    }

    private data class CacheEntry(val addresses: List<InetAddress>, val expiresAt: Long)

    private companion object {
        const val DNS_TIMEOUT_MS = 3_000
        const val CACHE_TTL_MS = 300_000L
    }
}

internal fun parsePublicIpv4Addresses(payload: String): List<InetAddress> =
    Regex("\\\"data\\\"\\s*:\\s*\\\"([0-9.]+)\\\"").findAll(payload)
        .mapNotNull { match -> runCatching { InetAddress.getByName(match.groupValues[1]) }.getOrNull() }
        .filter(InetAddress::isPublicAddress)
        .distinctBy(InetAddress::getHostAddress)
        .toList()

private fun InetAddress.isPublicAddress(): Boolean =
    !isAnyLocalAddress && !isLoopbackAddress && !isLinkLocalAddress && !isSiteLocalAddress && !isMulticastAddress

private fun BufferedInputStream.readAsciiLine(): String? {
    val output = ByteArrayOutputStream()
    while (true) {
        val value = read()
        if (value < 0) return output.takeIf { it.size() > 0 }?.toString(StandardCharsets.US_ASCII)
        if (value == '\n'.code) return output.toByteArray().let { bytes ->
            val length = bytes.size - if (bytes.lastOrNull() == '\r'.code.toByte()) 1 else 0
            String(bytes, 0, length, StandardCharsets.US_ASCII)
        }
        output.write(value)
    }
}

private fun BufferedInputStream.readChunked(): ByteArray {
    val output = ByteArrayOutputStream()
    while (true) {
        val size = readAsciiLine()?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: error("Invalid chunk size")
        if (size == 0) {
            while (readAsciiLine()?.isNotEmpty() == true) Unit
            return output.toByteArray()
        }
        output.write(readExactly(size))
        check(readAsciiLine()?.isEmpty() == true) { "Invalid chunk terminator" }
    }
}

private fun BufferedInputStream.readExactly(size: Int): ByteArray {
    require(size >= 0)
    val body = readNBytes(size)
    check(body.size == size) { "Incomplete HTTP body" }
    return body
}

private fun responseCharset(contentType: String?): Charset = contentType
    ?.let { Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
    ?.let { runCatching { Charset.forName(it.trim('"')) }.getOrNull() }
    ?: StandardCharsets.UTF_8
