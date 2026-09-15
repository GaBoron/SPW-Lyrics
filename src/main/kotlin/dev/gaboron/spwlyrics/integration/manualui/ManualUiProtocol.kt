package dev.gaboron.spwlyrics.integration.manualui

import kotlinx.serialization.Serializable

@Serializable
data class ManualUiRequest(
    val token: String,
    val action: String,
    val keywords: String? = null,
    val source: String? = null,
    val candidateKey: String? = null,
    val includeCached: Boolean = false,
)

@Serializable
data class ManualUiResponse(
    val ok: Boolean,
    val message: String = "",
    val activate: Boolean = false,
    val mode: String? = null,
    val track: ManualUiTrack? = null,
    val sources: List<ManualUiSource> = emptyList(),
    val candidates: List<ManualUiCandidate> = emptyList(),
    val preview: List<ManualUiPreviewLine> = emptyList(),
    val batch: BatchUiSnapshot? = null,
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

@Serializable
data class BatchUiSnapshot(
    val state: String,
    val stateLabel: String,
    val total: Int,
    val processed: Int,
    val completed: Int,
    val failed: Int,
    val cached: Int,
    val items: List<BatchUiItem>,
)

@Serializable
data class BatchUiItem(
    val key: String,
    val title: String,
    val artists: String,
    val album: String,
    val state: String,
    val stateLabel: String,
    val source: String,
    val quality: String,
    val message: String,
)
