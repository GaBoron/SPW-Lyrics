package dev.gaboron.spwlyrics.integration.manualui

import kotlinx.serialization.Serializable

@Serializable
data class ManualUiRequest(
    val token: String,
    val action: String,
    val keywords: String? = null,
    val source: String? = null,
    val candidateKey: String? = null,
)

@Serializable
data class ManualUiResponse(
    val ok: Boolean,
    val message: String = "",
    val track: ManualUiTrack? = null,
    val sources: List<ManualUiSource> = emptyList(),
    val candidates: List<ManualUiCandidate> = emptyList(),
    val preview: List<ManualUiPreviewLine> = emptyList(),
)

@Serializable
data class ManualUiTrack(
    val title: String,
    val artists: String,
    val album: String,
    val suggestedKeywords: String,
)

@Serializable
data class ManualUiSource(val id: String?, val name: String)

@Serializable
data class ManualUiCandidate(
    val key: String,
    val source: String,
    val title: String,
    val artists: String,
    val album: String,
    val duration: String,
    val quality: String,
    val score: Double,
)

@Serializable
data class ManualUiPreviewLine(val main: String, val secondary: String? = null)
