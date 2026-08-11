package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationMatchPolicyTest {
    @Test
    fun `accepts exact title and duration across artist aliases`() {
        assertTrue(TranslationMatchPolicy.accepts(score(title = 1.0, artist = 0.30, duration = 1.0)))
    }

    @Test
    fun `rejects a translation candidate with mismatched duration`() {
        assertFalse(TranslationMatchPolicy.accepts(score(title = 1.0, artist = 1.0, duration = 0.30)))
    }

    private fun score(title: Double, artist: Double, duration: Double) = CandidateScore(
        candidate = LyricsCandidate(LyricsSource.QQ, "id", "unravel", listOf("Artist")),
        score = 0.72,
        titleScore = title,
        artistScore = artist,
        albumScore = 0.0,
        durationScore = duration,
        versionConflict = false,
    )
}
