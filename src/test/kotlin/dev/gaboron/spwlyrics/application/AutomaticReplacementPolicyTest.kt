package dev.gaboron.spwlyrics.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticReplacementPolicyTest {
    @Test
    fun `each policy selects the intended load phase`() {
        assertTrue(AutomaticReplacementPolicy.ALWAYS.allowsAutomaticLoad(LyricsLoadPhase.BEFORE_LOCAL))
        assertFalse(AutomaticReplacementPolicy.ALWAYS.allowsAutomaticLoad(LyricsLoadPhase.AFTER_LOCAL_MISSING))
        assertFalse(AutomaticReplacementPolicy.WHEN_LOCAL_MISSING.allowsAutomaticLoad(LyricsLoadPhase.BEFORE_LOCAL))
        assertTrue(AutomaticReplacementPolicy.WHEN_LOCAL_MISSING.allowsAutomaticLoad(LyricsLoadPhase.AFTER_LOCAL_MISSING))
        assertFalse(AutomaticReplacementPolicy.MANUAL_ONLY.allowsAutomaticLoad(LyricsLoadPhase.BEFORE_LOCAL))
        assertFalse(AutomaticReplacementPolicy.MANUAL_ONLY.allowsAutomaticLoad(LyricsLoadPhase.AFTER_LOCAL_MISSING))
    }

    @Test
    fun `unknown config values preserve the existing always behavior`() {
        assertEquals(AutomaticReplacementPolicy.WHEN_LOCAL_MISSING, AutomaticReplacementPolicy.fromConfigValue("when_local_missing"))
        assertEquals(AutomaticReplacementPolicy.ALWAYS, AutomaticReplacementPolicy.fromConfigValue("unknown"))
    }
}
