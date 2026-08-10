package dev.gaboron.spwlyrics.integration

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheFolderOpenerTest {
    @Test
    fun `creates and opens the configured cache directory`() {
        val parent = Files.createTempDirectory("spw-cache-opener")
        val cache = parent.resolve("cache")
        var opened = java.nio.file.Path.of(".")
        val opener = CacheFolderOpener(cache) { opened = it }

        assertTrue(opener.open())
        assertTrue(Files.isDirectory(cache))
        assertEquals(cache, opened)
    }

    @Test
    fun `reports a desktop open failure without throwing`() {
        val cache = Files.createTempDirectory("spw-cache-open-failure")
        val opener = CacheFolderOpener(cache) { error("desktop unavailable") }

        assertEquals(false, opener.open())
    }
}
