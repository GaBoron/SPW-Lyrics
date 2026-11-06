package dev.gaboron.spwlyrics.provider

import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlinx.serialization.json.JsonObject

/** All LrcShare traffic shares one interruptible queue, including playback and batch work. */
internal class LrcShareClient(
    private val http: ProviderHttpClient,
    cacheDirectory: Path,
    private val clock: Clock,
) {
    private val store = LrcShareCatalogStore(cacheDirectory)
    private val lock = ReentrantLock(true)
    private var catalog: LrcShareCatalog? = null
    private var completedAt: Long? = null
    private var retryAfter: Long? = null
    private var rejectedSession: String? = null
    private var loaded = false

    fun search(title: String, artists: List<String>, limit: Int): JsonObject? {
        val requestedArtists = artists.map(String::trim).filter(String::isNotBlank)
        val fields = listOf("title" to title) + requestedArtists.map { "artist" to it }
        return search(fields, limit) { snapshot ->
            snapshot.contains(title) && requestedArtists.all { artist ->
                // A single artist parameter can also be a slash-separated list on the server.
                snapshot.contains(artist) || artist.split('/').map(String::trim).filter { it.length > 1 }
                    .takeIf { it.isNotEmpty() }?.all(snapshot::contains) == true
            }
        }
    }

    fun searchKeywords(keywords: String, limit: Int): JsonObject? {
        val tokens = keywords.trim().split(Regex("(?U)\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) return null
        // Keyword search admits a song when ANY token matches; never require the whole phrase.
        return search(listOf("keyword" to keywords), limit) { snapshot -> tokens.any(snapshot::contains) }
    }

    private fun search(fields: List<Pair<String, String>>, limit: Int,
        mayMatch: (LrcShareCatalog) -> Boolean): JsonObject? {
        if (limit <= 0) return null
        val parameters = fields.joinToString("&") { (name, value) -> "$name=${ProviderHttpClient.encode(value)}" }
        return request("/search?type=song&$parameters&limit=${limit.coerceAtMost(30)}&offset=0", mayMatch)
    }

    fun get(path: String): JsonObject = checkNotNull(request(path))

    private fun request(path: String, mayMatch: (LrcShareCatalog) -> Boolean = { true }): JsonObject? {
        lock.lockInterruptibly()
        try {
            val snapshot = currentCatalog()
            if (!mayMatch(snapshot)) return null
            return raw(path, snapshot.sessionToken)
        } finally {
            lock.unlock()
        }
    }

    private fun currentCatalog(): LrcShareCatalog {
        val now = clock.millis()
        val identity = LrcShareAccess.identity
        if (!loaded) {
            catalog = store.load(identity, now)
            loaded = true
        }
        catalog?.takeIf { it.isCurrent(identity, now) && it.sessionToken != rejectedSession }?.let { return it }
        val data = raw("/catalog")
        val fetched = try {
            LrcShareCatalog.fromResponse(data, identity, now)
        } catch (error: IllegalStateException) {
            retryAfter = System.nanoTime() + FAILURE_BACKOFF_NANOS
            throw error
        }
        catalog = fetched
        store.save(fetched)
        return fetched
    }

    private fun raw(path: String, session: String? = null): JsonObject {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("LrcShare request cancelled")
        check(retryAfter?.let { System.nanoTime() - it < 0 } != true) { "LrcShare 请求失败，稍后重试。" }
        completedAt?.let { previous ->
            val remaining = REQUEST_INTERVAL_NANOS - (System.nanoTime() - previous)
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining)
        }
        return try {
            val root = providerJson.parseToJsonElement(LrcShareAccess.request(http, path, session)) as JsonObject
            val code = root.long("code")
            check(code == 200L) { "LrcShare HTTP $code" }
            checkNotNull(root.obj("data")) { "LrcShare 响应缺少数据" }
        } catch (error: Exception) {
            if (error !is InterruptedException && !Thread.currentThread().isInterrupted) {
                // Avoid per-song retries during an outage, rejected session, or rate limit.
                retryAfter = System.nanoTime() + FAILURE_BACKOFF_NANOS
                if (error.message?.endsWith("HTTP 419") == true) {
                    rejectedSession = session
                }
            }
            throw error
        } finally {
            completedAt = System.nanoTime()
        }
    }

    private companion object {
        val REQUEST_INTERVAL_NANOS: Long = TimeUnit.MILLISECONDS.toNanos(300)
        val FAILURE_BACKOFF_NANOS: Long = TimeUnit.SECONDS.toNanos(30)
    }
}
