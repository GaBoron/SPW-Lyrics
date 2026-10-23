package dev.gaboron.spwlyrics.integration

object SpwLyricsActions {
    @JvmStatic
    fun openWorkspace() = PluginRuntime.openWorkspace()

    @JvmStatic
    fun openManualSearch() = PluginRuntime.openManualSearch()

    @JvmStatic
    fun openBatchProcessing() = PluginRuntime.openBatchProcessing()

    @JvmStatic
    fun openMetadataEditor() = PluginRuntime.openMetadataEditor()

    @JvmStatic
    fun openCacheFolder() = PluginRuntime.openCacheFolder()

    @JvmStatic
    fun resetLyricsOrder() = PluginRuntime.resetLyricsOrder()
}
