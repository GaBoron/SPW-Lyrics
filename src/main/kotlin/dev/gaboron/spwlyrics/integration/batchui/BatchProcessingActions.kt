package dev.gaboron.spwlyrics.integration.batchui

import dev.gaboron.spwlyrics.application.BatchMetadataOptions

/** Screen commands; the tutorial supplies an in-memory presentation without a batch processor. */
internal interface BatchProcessingActions {
    val state: BatchProcessingState
    fun refresh()
    fun includeCached(value: Boolean)
    fun select(key: String)
    fun selectAll(value: Boolean)
    fun start(metadata: BatchMetadataOptions? = null)
    fun pauseOrResume()
    fun stop()
    fun retry()
}
