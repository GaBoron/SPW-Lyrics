package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin

class SpwLyricsPlugin(pluginContext: PluginContext) : SpwPlugin(pluginContext) {
    override fun start() = PluginRuntime.install(pluginContext.pluginPath)
    override fun stop() = PluginRuntime.close()
}
