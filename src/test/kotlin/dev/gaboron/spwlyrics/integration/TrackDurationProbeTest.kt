package dev.gaboron.spwlyrics.integration

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackDurationProbeTest {
    @Test
    fun `reads a local duration once and caches it by path`() {
        val file = Files.createTempFile("spw-duration", ".mp3")
        var reads = 0
        val probe = CachedTrackDurationProbe {
            reads++
            183_456L
        }

        assertEquals(183_456L, probe.durationMs(file.toString()))
        assertEquals(183_456L, probe.durationMs(file.toString()))
        assertEquals(1, reads)
    }

    @Test
    fun `ignores paths that are not local files`() {
        val probe = CachedTrackDurationProbe { error("reader should not run") }

        assertEquals(null, probe.durationMs("https://example.invalid/song.mp3"))
    }
}
