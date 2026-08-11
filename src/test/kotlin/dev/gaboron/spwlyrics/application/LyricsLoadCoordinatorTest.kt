package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.LyricsProvider
import dev.gaboron.spwlyrics.storage.CachedLyrics
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import dev.gaboron.spwlyrics.storage.ManualOverride
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
            LyricsResolver(listOf(provider)),
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

        assertNull(coordinator.onLoad(a, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS))
        assertNull(coordinator.onLoad(b, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        Thread.sleep(180)
        assertEquals(1, reloads)
        assertTrue(coordinator.onLoad(b, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS)?.contains("B") == true)
        coordinator.close()
    }

    @Test
    fun `notifies once when every automatic provider fails`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-failure-cache"))
        val notified = CountDownLatch(1)
        val messages = mutableListOf<String>()
        val coordinator = LyricsLoadCoordinator(
            cache = cache,
            resolver = LyricsResolver(emptyList()),
            refreshBridge = object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics() = true
            },
            notify = { message ->
                synchronized(messages) { messages += message }
                notified.countDown()
            },
        )
        val query = TrackQuery("Missing", listOf("Artist"), "Album", path = "missing.mp3")

        assertNull(coordinator.onLoad(query, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS))
        assertTrue(notified.await(2, TimeUnit.SECONDS))
        assertNull(coordinator.onLoad(query, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS))
        Thread.sleep(50)

        assertEquals(
            listOf("自动加载歌词失败，请在“设置 → 创意工坊 → 模组设置 → SPW Lyrics → 手动搜索歌词”中手动匹配。"),
            synchronized(messages) { messages.toList() },
        )
        coordinator.close()
    }

    @Test
    fun `local-missing policy searches only after SPW local loading fails`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-local-missing-cache"))
        val reloaded = CountDownLatch(1)
        var searches = 0
        val provider = object : LyricsProvider {
            override val source = LyricsSource.AMLL
            override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> {
                searches++
                return listOf(LyricsCandidate(source, query.title, query.title, query.artists, query.album))
            }

            override fun fetch(candidate: LyricsCandidate) =
                LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "fetched")))
        }
        val coordinator = LyricsLoadCoordinator(
            cache,
            LyricsResolver(listOf(provider)),
            object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics(): Boolean {
                    reloaded.countDown()
                    return true
                }
            },
        )
        val query = TrackQuery("Missing locally", listOf("Artist"), "Album", path = "missing-locally.mp3")

        assertNull(coordinator.onLoad(query, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.WHEN_LOCAL_MISSING))
        Thread.sleep(50)
        assertEquals(0, searches)

        assertNull(coordinator.onLoad(query, LyricsLoadPhase.AFTER_LOCAL_MISSING, AutomaticReplacementPolicy.WHEN_LOCAL_MISSING))
        assertTrue(reloaded.await(2, TimeUnit.SECONDS))
        assertTrue(searches > 0)
        assertTrue(
            coordinator.onLoad(
                query,
                LyricsLoadPhase.AFTER_LOCAL_MISSING,
                AutomaticReplacementPolicy.WHEN_LOCAL_MISSING,
            )?.contains("fetched") == true,
        )
        coordinator.close()
    }

    @Test
    fun `manual selection remains active when automatic replacement is disabled`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-manual-only-cache"))
        val query = TrackQuery("Manual", listOf("Artist"), "Album", path = "manual.mp3")
        val candidate = LyricsCandidate(LyricsSource.AMLL, "manual", query.title, query.artists, query.album)
        val document = LyricsDocument(LyricsSource.AMLL, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "manual")))
        cache.putOverride(query, ManualOverride(local = false, source = candidate.source, candidate = candidate))
        cache.putLyrics(query, CachedLyrics(document, "[00:01.000]manual", System.currentTimeMillis()))
        val coordinator = LyricsLoadCoordinator(
            cache,
            LyricsResolver(emptyList()),
            object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics() = true
            },
        )

        assertEquals(
            "[00:01.000]manual",
            coordinator.onLoad(query, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.MANUAL_ONLY),
        )
        coordinator.close()
    }

    @Test
    fun `restoring automatic matching clears manual lock and cached lyrics`() {
        val cache = FileLyricsCache(createTempDirectory("coordinator-restore-automatic-cache"))
        val query = TrackQuery("Song", listOf("Artist"), "Album", path = "song.mp3")
        val manual = LyricsCandidate(LyricsSource.QQ, "manual", query.title, query.artists, query.album)
        cache.putOverride(query, ManualOverride(local = false, source = LyricsSource.QQ, candidate = manual))
        cache.putLyrics(
            query,
            CachedLyrics(
                LyricsDocument(LyricsSource.QQ, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "old"))),
                "old",
                System.currentTimeMillis(),
            ),
        )
        val reloaded = CountDownLatch(1)
        val provider = object : LyricsProvider {
            override val source = LyricsSource.AMLL
            override fun search(query: TrackQuery, keywords: String, limit: Int) = listOf(
                LyricsCandidate(source, "automatic", query.title, query.artists, query.album),
            )
            override fun fetch(candidate: LyricsCandidate) =
                LyricsDocument(source, LyricsFormat.LRC, listOf(LyricLine(1_000, 2_000, "automatic")))
        }
        val coordinator = LyricsLoadCoordinator(
            cache,
            LyricsResolver(listOf(provider)),
            object : LyricsRefreshBridge {
                override fun reloadCurrentLyrics(): Boolean {
                    reloaded.countDown()
                    return true
                }
            },
        )
        assertEquals("old", coordinator.onLoad(query, LyricsLoadPhase.BEFORE_LOCAL, AutomaticReplacementPolicy.ALWAYS))

        assertTrue(coordinator.useAutomatic())
        assertTrue(reloaded.await(2, TimeUnit.SECONDS))

        assertNull(cache.getOverride(query))
        assertEquals(LyricsSource.AMLL, cache.getLyrics(query)?.document?.source)
        coordinator.close()
    }
}
