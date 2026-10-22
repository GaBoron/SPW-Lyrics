package dev.gaboron.spwlyrics.integration

object SpwLyricsActions {
    @JvmStatic
    fun openWorkspace() = PluginRuntime.openWorkspace()

    @JvmStatic
    fun openStandaloneWorkspace() = PluginRuntime.openStandaloneWorkspace()

    @JvmStatic
    fun openManualSearch() = PluginRuntime.openManualSearch()

    @JvmStatic
    fun openBatchProcessing() = PluginRuntime.openBatchProcessing()

    @JvmStatic
    fun openMetadataEditor() = PluginRuntime.openMetadataEditor()

    @JvmStatic
    fun openCacheFolder() = PluginRuntime.openCacheFolder()
}
