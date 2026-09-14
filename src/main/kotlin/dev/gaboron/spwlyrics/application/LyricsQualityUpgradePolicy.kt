package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality

/** Keeps automatic fallback lyrics replaceable until the finest parsed timing granularity is found. */
internal object LyricsQualityUpgradePolicy {
    fun shouldUpgrade(document: LyricsDocument): Boolean = document.quality != LyricsQuality.CHARACTER_SYNCED

    fun canReplace(current: LyricsDocument, replacement: LyricsDocument): Boolean =
        replacement.quality.rank > current.quality.rank
}
