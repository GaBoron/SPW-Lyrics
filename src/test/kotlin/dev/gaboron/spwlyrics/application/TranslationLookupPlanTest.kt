package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationLookupPlanTest {
    @Test
    fun `keeps local and confirmed AM metadata as multilingual lookup alternatives`() {
        val local = TrackQuery(
            title = "Into the Night",
            artists = listOf("YOASOBI"),
            album = "English EP",
            durationMs = 240_000,
        )
        val apple = LyricsCandidate(
            source = LyricsSource.APPLE_MUSIC,
            remoteId = "apple",
            title = "夜に駆ける",
            artists = listOf("YOASOBI"),
            album = "THE BOOK",
            durationMs = 240_400,
        )

        val queries = TranslationLookupPlan.queries(local, apple)

        assertEquals(listOf("Into the Night", "夜に駆ける"), queries.map(TrackQuery::title))
        assertEquals(240_000, queries.last().durationMs)
    }
}
