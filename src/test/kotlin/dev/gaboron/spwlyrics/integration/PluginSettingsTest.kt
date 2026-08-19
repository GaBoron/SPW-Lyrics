package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(UnstableSpwWorkshopApi::class)
class PluginSettingsTest {
    @Test
    fun `tracks changes saved through the SPW config manager`() {
        val helper = FakeConfigHelper(
            mutableMapOf(
                PluginSettings.REPLACEMENT_POLICY_KEY to "when_local_missing",
                PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY to false,
            ),
        )
        val manager = FakeConfigManager(helper)
        val shortcutChanges = mutableListOf<Boolean>()
        val settings = PluginSettings(manager, shortcutChanges::add)

        assertEquals(AutomaticReplacementPolicy.WHEN_LOCAL_MISSING, settings.automaticReplacementPolicy())
        assertFalse(settings.manualSearchShortcutEnabled())

        helper.set(PluginSettings.REPLACEMENT_POLICY_KEY, "manual_only")
        helper.set(PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, true)
        manager.notifyChanged()

        assertEquals(AutomaticReplacementPolicy.MANUAL_ONLY, settings.automaticReplacementPolicy())
        assertTrue(settings.manualSearchShortcutEnabled())
        assertEquals(listOf(true), shortcutChanges)
        manager.notifyChanged()
        assertEquals(listOf(true), shortcutChanges)
        settings.close()
        assertEquals(null, manager.listener)
    }

    private class FakeConfigManager(private val helper: ConfigHelper) : ConfigManager {
        var listener: Consumer<ConfigHelper>? = null

        override fun getConfig(): ConfigHelper = helper
        override fun getConfig(fileName: String): ConfigHelper = helper
        override fun addConfigChangeListener(listener: Consumer<ConfigHelper>) {
            this.listener = listener
        }

        override fun addConfigChangeListener(fileName: String, listener: Consumer<ConfigHelper>) {
            this.listener = listener
        }

        override fun removeConfigChangeListener(listener: Consumer<ConfigHelper>) {
            if (this.listener === listener) this.listener = null
        }

        fun notifyChanged() {
            listener?.accept(helper)
        }
    }

    private class FakeConfigHelper(private val values: MutableMap<String, Any>) : ConfigHelper {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T): T = values[key] as? T ?: defaultValue

        override fun set(key: String, value: Any) {
            values[key] = value
        }

        override fun save() = true
        override fun reload() = true
        override fun getConfigPath(): Path = Path.of("spw-lyrics.json")
    }
}
