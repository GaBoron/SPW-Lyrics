package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferredSourceMatchPolicyTest {
    @Test
    fun `accepts relaxed AMLL metadata`() {
        assertTrue(PreferredSourceMatchPolicy.accepts(score(LyricsSource.AMLL, 0.78, 0.72, 0.74)))
    }

    @Test
    fun `accepts Apple regional metadata with matching recording evidence`() {
        assertTrue(
            PreferredSourceMatchPolicy.accepts(
                score(LyricsSource.APPLE_MUSIC, title = 0.50, artist = 0.95, total = 0.69, duration = 1.0),
            ),
        )
    }

    @Test
    fun `does not relax ordinary sources or conflicting Apple versions`() {
        assertFalse(PreferredSourceMatchPolicy.accepts(score(LyricsSource.QQ, 0.78, 0.72, 0.74)))
        assertFalse(
            PreferredSourceMatchPolicy.accepts(
                score(LyricsSource.APPLE_MUSIC, 0.80, 0.90, 0.80, duration = 0.30),
            ),
        )
    }

    private fun score(
        source: LyricsSource,
        title: Double,
        artist: Double,
        total: Double,
        duration: Double? = null,
    ) = CandidateScore(
        candidate = LyricsCandidate(source, "id", "Song", listOf("Artist")),
        score = total,
        titleScore = title,
        artistScore = artist,
        albumScore = null,
        durationScore = duration,
        versionConflict = false,
    )
}
