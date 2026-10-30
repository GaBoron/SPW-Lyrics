package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import dev.gaboron.spwlyrics.domain.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance

@OptIn(UnstableSpwWorkshopApi::class)
class PluginSettings(
    private val manager: ConfigManager,
    private val onSelectionChanged: () -> Unit = {},
    private val notify: (String) -> Unit = {},
    private val performance: PluginPerformance = PluginPerformance(),
) : AutoCloseable {
    private val initialConfig = manager.getConfig(CONFIG_FILE)
    private val replacementPolicy = AtomicReference(readPolicy(initialConfig))
    private val selection = AtomicReference(readSelection(initialConfig).getOrDefault(LyricsPreferences()))
    private val shareKey = AtomicReference(initialConfig.get("lrcshare_api_key", "").trim())
    private var invalidInput: String? = null
    private val lock = Any()
    @Volatile private var closed = false
    private var revision = 0L
    private var pending: Future<*>? = null
    private val worker = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "spw-lyrics-config").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private val listener = Consumer<ConfigHelper> { config ->
        performance.measure(PluginPerformance.Operation.CONFIG_CALLBACK) {
            synchronized(lock) {
                if (!closed) {
                    val expected = ++revision
                    pending?.cancel(false)
                    pending = worker.schedule({
                        runCatching {
                            performance.measure(PluginPerformance.Operation.CONFIG_RELOAD) { reload(config, expected) }
                        }.onFailure {
                            if (!closed) System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "重新读取歌词设置失败", it)
                        }
                    }, 40, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private fun reload(config: ConfigHelper, expected: Long) {
        // SPW saves settings through a separate helper; the callback can still hold cached values.
        if (!config.reload()) return
        val nextPolicy = readPolicy(config)
        val next = readSelection(config)
        val key = config.get("lrcshare_api_key", "").trim()
        val selectionChanged = synchronized(lock) {
            if (closed || revision != expected) return
            val policyChanged = replacementPolicy.getAndSet(nextPolicy) != nextPolicy
            val changed = next.getOrNull()?.let { value -> selection.getAndSet(value) != value } ?: false
            val invalid = next.exceptionOrNull()?.message
            if (invalid != null && invalid != invalidInput) notify("排序未生效，已保留上一次有效设置。$invalid")
            invalidInput = invalid
            shareKey.getAndSet(key) != key || changed || policyChanged
        }
        if (!closed && selectionChanged) onSelectionChanged()
    }

    init {
        manager.addConfigChangeListener(CONFIG_FILE, listener)
        readSelection(initialConfig).exceptionOrNull()?.message?.let { notify("排序配置无效，暂用默认顺序。$it") }
    }

    fun automaticReplacementPolicy(): AutomaticReplacementPolicy = replacementPolicy.get()
    fun lyricsPreferences(): LyricsPreferences = selection.get()
    fun lrcShareApiKey(): String = shareKey.get()

    private fun readSelection(config: ConfigHelper): Result<LyricsPreferences> = runCatching {
        LyricsPreferenceInput.parse(config.get("lyrics_priority_mode", "type_first"),
            config.get("lyrics_quality_order", LyricsPreferenceInput.DEFAULT_QUALITIES),
            config.get("lyrics_source_order", LyricsPreferenceInput.DEFAULT_SOURCES))
    }

    fun resetSelection(): Boolean {
        val config = manager.getConfig(CONFIG_FILE)
        if (!config.reload()) return false
        config.set("lyrics_priority_mode", "type_first")
        config.set("lyrics_quality_order", LyricsPreferenceInput.DEFAULT_QUALITIES)
        config.set("lyrics_source_order", LyricsPreferenceInput.DEFAULT_SOURCES)
        if (!config.save()) return false
        invalidInput = null
        if (selection.getAndSet(LyricsPreferences()) != LyricsPreferences()) onSelectionChanged()
        return true
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            revision++
            pending?.cancel(true)
        }
        worker.shutdownNow()
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
