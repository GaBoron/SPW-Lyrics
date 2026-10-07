package dev.gaboron.spwlyrics.application

enum class LyricsResolutionStage { SEARCHING, SELECTING }

data class LyricsResolutionProgress(
    val stage: LyricsResolutionStage,
    val fraction: Double,
    val detail: String,
)
