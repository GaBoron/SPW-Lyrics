package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsLoadCoordinatorTest {
    @Test
    fun `cache miss returns immediately then reloads only current track`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-cache"))
        val finished = CountDownLatch(1)
        var reloads = 0
        val provider = object : LyricsProvider {
            override val source = LyricsSource.AMLL
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, query.title, query.title, query.artists, query.album),
            )
            override fun fetch(candidate: LyricsCandidate): LyricsDocument {
                Thread.sleep(if (candidate.title == "A") 120 else 10)
                return LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, candidate.title)))
            }
        }
        val coordinator = LyricsLoadCoordinator(
            cache,
            LyricsResolver(listOf(provider), cache),
            object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics(): Boolean {
                    reloads++
                    finished.countDown()
                    return true
                }
            },
        )
        val a = TrackQuery("A", listOf("Artist"), "Album", path = "A.mp3")
        val b = TrackQuery("B", listOf("Artist"), "Album", path = "B.mp3")

        assertNull(coordinator.onBeforeLoad(a))
        assertNull(coordinator.onBeforeLoad(b))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        Thread.sleep(180)
        assertEquals(1, reloads)
        assertTrue(coordinator.onBeforeLoad(b)?.contains("B") == true)
        coordinator.close()
    }

    @Test
    fun `notifies once when every automatic provider fails`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-failure-cache"))
        val notified = CountDownLatch(1)
        val messages = mutableListOf<String>()
        val coordinator = LyricsLoadCoordinator(
            cache = cache,
            resolver = LyricsResolver(emptyList(), cache),
            refreshBridge = object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics() = true
            },
            notify = { message ->
                synchronized(messages) { messages += message }
                notified.countDown()
            },
        )
        val query = TrackQuery("Missing", listOf("Artist"), "Album", path = "missing.mp3")

        assertNull(coordinator.onBeforeLoad(query))
        assertTrue(notified.await(2, TimeUnit.SECONDS))
        assertNull(coordinator.onBeforeLoad(query))
        Thread.sleep(50)

        assertEquals(
            listOf("自动加载歌词失败，请在“设置 → 模组管理 → SPW Lyrics → 手动搜索歌词”中手动匹配。"),
            synchronized(messages) { messages.toList() },
        )
        coordinator.close()
    }
}
