package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality

/** Keeps automatic fallback lyrics replaceable until a karaoke timeline is found. */
internal object LyricsQualityUpgradePolicy {
    fun shouldUpgrade(document: LyricsDocument): Boolean = document.quality != LyricsQuality.KARAOKE_SYNCED
}
