package dev.gaboron.spwlyrics.integration

import com.xuncorp.pisces.PiscesMediaItem
import com.xuncorp.pisces.PiscesPlayer
import com.xuncorp.spw.testing.PlaybackRoot
import com.xuncorp.voxzen.service.PlaybackService
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObjectGraphProbeTest {
    @Test
    fun `finds nested target without traversing unrelated branches`() {
        val target = Target()
        val root = Root(Allowed(target), Disallowed(Target()))

        val found = ObjectGraphProbe.find(
            roots = listOf(root),
            maxDepth = 3,
            matches = { it === target },
            mayTraverse = { it.name.startsWith(javaClass.packageName) && it != Disallowed::class.java },
        )

        assertSame(target, found)
    }

    @Test
    fun `reloads the current media item through the SPW update lyrics bridge`() {
        val first = PiscesMediaItem("first")
        val second = PiscesMediaItem("second")
        val player = PiscesPlayer(first)
        val reloader = SpwPlaybackLyricsReloader { listOf(PlaybackRoot(PlaybackService(player))) }

        assertTrue(reloader.reload())
        assertSame(first, PlaybackService.lastUpdated)

        player.current = second
        assertTrue(reloader.reload())
        assertSame(second, PlaybackService.lastUpdated)
    }

    private class Root(val allowed: Allowed, val disallowed: Disallowed)
    private class Allowed(val target: Target)
    private class Disallowed(val target: Target)
    private class Target
}
