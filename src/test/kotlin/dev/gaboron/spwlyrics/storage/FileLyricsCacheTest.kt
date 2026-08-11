package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileLyricsCacheTest {
    @Test
    fun `persists encoded lyrics and manual overrides`() {
        val root = createTempDirectory("spw-lyrics-cache-test")
        val clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val cache = FileLyricsCache(root, clock)
        val lyricsQuery = TrackQuery("Song: Live", listOf("Artist/Guest"), "Album")
        val overrideQuery = TrackQuery("Other Song", listOf("Other Artist"), "Album")
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.LRC,
            lines = listOf(LyricLine(0, 1_000, "hello")),
        )

        cache.putLyrics(lyricsQuery, CachedLyrics(document, "[00:00.000]hello", clock.millis()))
        cache.putOverride(overrideQuery, ManualOverride(local = true))

        assertEquals("[00:00.000]hello", cache.getLyrics(lyricsQuery)?.encoded)
        assertTrue(cache.getOverride(overrideQuery)!!.local)
        assertTrue(Files.list(root.resolve("歌词")).use { files ->
            files.anyMatch { it.fileName.toString().startsWith("Artist_Guest - Song_ Live [") }
        })
        assertTrue(Files.list(root.resolve("手动匹配")).use { files ->
            files.anyMatch { it.fileName.toString().startsWith("Other Artist - Other Song [") }
        })
        assertTrue(Files.isRegularFile(root.resolve("缓存说明.txt")))
    }

    @Test
    fun `persists a manually selected provider candidate`() {
        val root = createTempDirectory("spw-manual-source-cache-test")
        val candidate = dev.gaboron.spwlyrics.domain.LyricsCandidate(
            LyricsSource.QQ,
            "remote-id",
            "Song",
            listOf("Artist"),
            "Album",
        )
        val query = TrackQuery("Song", listOf("Artist"), "Album")
        FileLyricsCache(root).putOverride(
            query,
            ManualOverride(local = false, source = LyricsSource.QQ, candidate = candidate),
        )

        assertEquals(candidate, FileLyricsCache(root).getOverride(query)?.candidate)
    }

    @Test
    fun `removes lyrics and manual override for one track`() {
        val root = createTempDirectory("spw-cache-removal-test")
        val query = TrackQuery("Song", listOf("Artist"), "Album")
        val document = LyricsDocument(LyricsSource.QQ, LyricsFormat.LRC, listOf(LyricLine(0, 1_000, "cached")))
        val cache = FileLyricsCache(root)
        cache.putLyrics(query, CachedLyrics(document, "cached", System.currentTimeMillis()))
        cache.putOverride(query, ManualOverride(local = true))

        cache.removeLyrics(query)
        cache.removeOverride(query)

        assertEquals(null, cache.getLyrics(query))
        assertEquals(null, cache.getOverride(query))
    }

    @Test
    fun `removes legacy flat cache files instead of maintaining compatibility paths`() {
        val root = createTempDirectory("spw-legacy-cache-test")
        val legacyLyrics = root.resolve("lyrics-${"a".repeat(64)}.json")
        val legacyOverride = root.resolve("override-${"b".repeat(64)}.json")
        Files.writeString(legacyLyrics, "{}")
        Files.writeString(legacyOverride, "{}")

        FileLyricsCache(root)

        assertFalse(Files.exists(legacyLyrics))
        assertFalse(Files.exists(legacyOverride))
    }
}
