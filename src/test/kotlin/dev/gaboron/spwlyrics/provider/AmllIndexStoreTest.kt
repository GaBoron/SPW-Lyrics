package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmllIndexStoreTest {
    @Test
    fun `refreshes a stale index without restarting the provider`() {
        val cache = createTempDirectory("spw-amll-refresh-test")
        val clock = MutableClock(Instant.parse("2026-08-19T00:00:00Z"))
        val http = SequencedHttp({ indexRecord("first") }, { indexRecord("second") })
        val store = AmllIndexStore(cache, http, clock)

        assertEquals("first", store.search(QUERY, "Song Artist", 5).single().remoteId)
        clock.advance(Duration.ofHours(23))
        assertEquals("first", store.search(QUERY, "Song Artist", 5).single().remoteId)
        assertEquals(1, http.getCalls)

        clock.advance(Duration.ofHours(1))
        assertEquals("second", store.search(QUERY, "Song Artist", 5).single().remoteId)
        assertEquals(2, http.getCalls)
    }

    @Test
    fun `retries a failed stale refresh after one hour while keeping the old index`() {
        val cache = createTempDirectory("spw-amll-retry-test")
        val clock = MutableClock(Instant.parse("2026-08-19T00:00:00Z"))
        val http = SequencedHttp(
            { indexRecord("first") },
            { error("network unavailable") },
            { indexRecord("second") },
        )
        val store = AmllIndexStore(cache, http, clock)

        assertEquals("first", store.search(QUERY, "Song Artist", 5).single().remoteId)
        clock.advance(Duration.ofDays(1))
        assertEquals("first", store.search(QUERY, "Song Artist", 5).single().remoteId)

        clock.advance(Duration.ofMinutes(59))
        assertEquals("first", store.search(QUERY, "Song Artist", 5).single().remoteId)
        assertEquals(2, http.getCalls)

        clock.advance(Duration.ofMinutes(1))
        assertEquals("second", store.search(QUERY, "Song Artist", 5).single().remoteId)
        assertEquals(3, http.getCalls)
    }

    @Test
    fun `moves the legacy index into the unified cache directory`() {
        val root = createTempDirectory("spw-amll-migration-test")
        val legacy = root.resolve("amll").resolve("amll-index.jsonl")
        val cache = root.resolve("cache").resolve("AMLL 索引")
        val now = Instant.parse("2026-08-19T00:00:00Z")
        Files.createDirectories(legacy.parent)
        Files.writeString(legacy, indexRecord("legacy"))
        Files.setLastModifiedTime(legacy, FileTime.from(now))
        val http = SequencedHttp()
        val store = AmllIndexStore(cache, http, Clock.fixed(now, ZoneOffset.UTC), legacy)

        assertEquals("legacy", store.search(QUERY, "Song Artist", 5).single().remoteId)
        assertTrue(Files.isRegularFile(cache.resolve("index.jsonl")))
        assertFalse(Files.exists(legacy))
        assertEquals(0, http.getCalls)
    }

    private companion object {
        val QUERY = TrackQuery("Song", listOf("Artist"), "Album")

        fun indexRecord(id: String): String =
            """{"id":"$id","metadata":[["musicName",["Song"]],["artists",["Artist"]],["album",["Album"]]]}"""
    }
}

private class MutableClock(
    private var current: Instant,
    private val currentZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

private class SequencedHttp(vararg responses: () -> String) : ProviderHttp {
    private val pending = ArrayDeque(responses.toList())
    var getCalls: Int = 0
        private set

    override fun get(url: String, headers: Map<String, String>): String {
        getCalls++
        return pending.removeFirst().invoke()
    }

    override fun postJson(url: String, body: String, headers: Map<String, String>): String =
        error("not used")

    override fun postForm(url: String, values: Map<String, String>, headers: Map<String, String>): String =
        error("not used")
}
