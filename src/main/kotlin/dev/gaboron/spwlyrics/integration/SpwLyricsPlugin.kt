package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin

class SpwLyricsPlugin(pluginContext: PluginContext) : SpwPlugin(pluginContext) {
    private val ownerId = pluginContext.pluginId
    override fun start() = PluginRuntime.install(ownerId)
    override fun stop() = PluginRuntime.close()
}
