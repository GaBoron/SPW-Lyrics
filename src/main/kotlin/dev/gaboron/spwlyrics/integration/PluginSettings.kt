package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@OptIn(UnstableSpwWorkshopApi::class)
class PluginSettings(
    private val manager: ConfigManager,
) : AutoCloseable {
    private val replacementPolicy = AtomicReference(readPolicy(manager.getConfig(CONFIG_FILE)))
    private val listener = Consumer<ConfigHelper> { replacementPolicy.set(readPolicy(it)) }

    init {
        manager.addConfigChangeListener(CONFIG_FILE, listener)
    }

    fun automaticReplacementPolicy(): AutomaticReplacementPolicy = replacementPolicy.get()

    override fun close() {
        manager.removeConfigChangeListener(listener)
    }

    private fun readPolicy(config: ConfigHelper): AutomaticReplacementPolicy =
        AutomaticReplacementPolicy.fromConfigValue(
            config.get(REPLACEMENT_POLICY_KEY, AutomaticReplacementPolicy.ALWAYS.configValue),
        )

    companion object {
        const val CONFIG_FILE = "spw-lyrics.json"
        const val REPLACEMENT_POLICY_KEY = "automatic_replacement_policy"
    }
}
