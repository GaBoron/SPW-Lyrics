package dev.gaboron.spwlyrics.provider

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

interface ProviderHttp {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String
    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
    fun postForm(url: String, values: Map<String, String>, headers: Map<String, String> = emptyMap()): String
}

class ProviderHttpClient(
    connectTimeout: Duration = Duration.ofSeconds(2),
    private val requestTimeout: Duration = Duration.ofSeconds(3),
) : ProviderHttp {
    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun get(url: String, headers: Map<String, String>): String = send(
        HttpRequest.newBuilder(URI.create(url)).GET(),
        headers,
    )

    override fun postJson(url: String, body: String, headers: Map<String, String>): String = send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        headers,
    )

    override fun postForm(url: String, values: Map<String, String>, headers: Map<String, String>): String {
        val body = values.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
            headers,
        )
    }

    private fun send(builder: HttpRequest.Builder, headers: Map<String, String>): String {
        builder.timeout(requestTimeout)
            .header("User-Agent", USER_AGENT)
        headers.forEach(builder::header)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
        return response.body()
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 SPW-Lyrics/0.1.0"
        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
