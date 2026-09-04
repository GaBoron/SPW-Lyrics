package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import dev.gaboron.spwlyrics.integration.shortcut.ManualSearchShortcutController
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

        helper.saveExternally(PluginSettings.REPLACEMENT_POLICY_KEY, "manual_only")
        helper.saveExternally(PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, true)
        manager.notifyChanged()

        assertEquals(AutomaticReplacementPolicy.MANUAL_ONLY, settings.automaticReplacementPolicy())
        assertTrue(settings.manualSearchShortcutEnabled())
        assertEquals(listOf(true), shortcutChanges)
        manager.notifyChanged()
        assertEquals(listOf(true), shortcutChanges)
        settings.close()
        assertEquals(null, manager.listener)
    }

    @Test
    fun `saved switch changes disable and reenable the shortcut without restarting`() {
        val helper = FakeConfigHelper(mutableMapOf())
        val manager = FakeConfigManager(helper)
        var starts = 0
        var activeRegistrations = 0
        val controller = ManualSearchShortcutController(
            onPressed = {},
            onFailure = { throw it },
            startShortcut = { _, _ ->
                starts++
                activeRegistrations++
                AutoCloseable { activeRegistrations-- }
            },
        )
        val settings = PluginSettings(manager, controller::setEnabled)
        try {
            controller.setEnabled(settings.manualSearchShortcutEnabled())
            assertEquals(1, activeRegistrations)

            helper.saveExternally(PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, false)
            manager.notifyChanged()
            assertFalse(settings.manualSearchShortcutEnabled())
            assertEquals(0, activeRegistrations)
            manager.notifyChanged()
            assertEquals(0, activeRegistrations)

            helper.saveExternally(PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, true)
            manager.notifyChanged()
            assertTrue(settings.manualSearchShortcutEnabled())
            assertEquals(1, activeRegistrations)
            manager.notifyChanged()
            assertEquals(2, starts)
        } finally {
            settings.close()
            controller.close()
        }
        assertEquals(0, activeRegistrations)
        assertEquals(null, manager.listener)
    }

    @Test
    fun `keeps the last settings when reload fails and applies a later successful reload`() {
        val helper = FakeConfigHelper(mutableMapOf())
        val manager = FakeConfigManager(helper)
        val shortcutChanges = mutableListOf<Boolean>()
        val settings = PluginSettings(manager, shortcutChanges::add)
        try {
            helper.saveExternally(PluginSettings.MANUAL_SEARCH_SHORTCUT_ENABLED_KEY, false)
            helper.saveExternally(PluginSettings.REPLACEMENT_POLICY_KEY, "manual_only")
            helper.reloadSucceeds = false
            manager.notifyChanged()
            assertTrue(settings.manualSearchShortcutEnabled())
            assertEquals(AutomaticReplacementPolicy.ALWAYS, settings.automaticReplacementPolicy())
            assertTrue(shortcutChanges.isEmpty())

            helper.reloadSucceeds = true
            manager.notifyChanged()
            assertFalse(settings.manualSearchShortcutEnabled())
            assertEquals(AutomaticReplacementPolicy.MANUAL_ONLY, settings.automaticReplacementPolicy())
            assertEquals(listOf(false), shortcutChanges)
        } finally {
            settings.close()
        }
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
        private val savedValues = values.toMutableMap()
        var reloadSucceeds = true

        fun saveExternally(key: String, value: Any) {
            savedValues[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T): T = values[key] as? T ?: defaultValue

        override fun set(key: String, value: Any) {
            values[key] = value
        }

        override fun save(): Boolean {
            savedValues.clear()
            savedValues.putAll(values)
            return true
        }

        override fun reload(): Boolean {
            if (!reloadSucceeds) return false
            values.clear()
            values.putAll(savedValues)
            return true
        }
        override fun getConfigPath(): Path = Path.of("spw-lyrics.json")
    }
}
