package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLyricsCacheTest {
    @Test
    fun `persists encoded lyrics and manual overrides`() {
        val root = createTempDirectory("spw-lyrics-cache-test")
        val clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val cache = FileLyricsCache(root, clock)
        val document = LyricsDocument(
            source = LyricsSource.AMLL,
            format = LyricsFormat.LRC,
            lines = listOf(LyricLine(0, 1_000, "hello")),
        )

        cache.putLyrics("a".repeat(64), CachedLyrics(document, "[00:00.000]hello", clock.millis()))
        cache.putOverride("b".repeat(64), ManualOverride(local = true))

        assertEquals("[00:00.000]hello", cache.getLyrics("a".repeat(64))?.encoded)
        assertTrue(cache.getOverride("b".repeat(64))!!.local)
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
        FileLyricsCache(root).putOverride(
            "c".repeat(64),
            ManualOverride(local = false, source = LyricsSource.QQ, candidate = candidate),
        )

        assertEquals(candidate, FileLyricsCache(root).getOverride("c".repeat(64))?.candidate)
    }
}
