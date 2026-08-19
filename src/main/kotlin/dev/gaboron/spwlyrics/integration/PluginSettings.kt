package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@OptIn(UnstableSpwWorkshopApi::class)
class PluginSettings(
    private val manager: ConfigManager,
    private val onManualSearchShortcutEnabledChanged: (Boolean) -> Unit = {},
) : AutoCloseable {
    private val initialConfig = manager.getConfig(CONFIG_FILE)
    private val replacementPolicy = AtomicReference(readPolicy(initialConfig))
    private val manualSearchShortcutEnabled = AtomicBoolean(readManualSearchShortcutEnabled(initialConfig))
    private val listener = Consumer<ConfigHelper> {
        replacementPolicy.set(readPolicy(it))
        val enabled = readManualSearchShortcutEnabled(it)
        if (manualSearchShortcutEnabled.getAndSet(enabled) != enabled) {
            onManualSearchShortcutEnabledChanged(enabled)
        }
    }

    init {
        manager.addConfigChangeListener(CONFIG_FILE, listener)
    }

    fun automaticReplacementPolicy(): AutomaticReplacementPolicy = replacementPolicy.get()
    fun manualSearchShortcutEnabled(): Boolean = manualSearchShortcutEnabled.get()

    override fun close() {
        manager.removeConfigChangeListener(listener)
    }

    private fun readPolicy(config: ConfigHelper): AutomaticReplacementPolicy =
        AutomaticReplacementPolicy.fromConfigValue(
            config.get(REPLACEMENT_POLICY_KEY, AutomaticReplacementPolicy.ALWAYS.configValue),
        )

    private fun readManualSearchShortcutEnabled(config: ConfigHelper): Boolean =
        config.get(MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, true)

    companion object {
        const val CONFIG_FILE = "spw-lyrics.json"
        const val REPLACEMENT_POLICY_KEY = "automatic_replacement_policy"
        const val MANUAL_SEARCH_SHORTCUT_ENABLED_KEY = "manual_search_shortcut_enabled"
    }
}
