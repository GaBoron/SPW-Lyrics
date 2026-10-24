package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin

class SpwLyricsPlugin(pluginContext: PluginContext) : SpwPlugin(pluginContext) {
    private val ownerId = pluginContext.pluginId
    private val channel = pluginContext.spwChannel
    override fun start() = PluginRuntime.install(ownerId, channel)
    override fun stop() = PluginRuntime.close()
}
