package dev.gaboron.spwlyrics.application

enum class LyricsResolutionStage { SEARCHING, SELECTING, TRANSLATING }

data class LyricsResolutionProgress(
    val stage: LyricsResolutionStage,
    val fraction: Double,
    val detail: String,
)
