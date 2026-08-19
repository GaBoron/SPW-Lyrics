package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import dev.gaboron.spwlyrics.application.LyricsLoadPhase
import dev.gaboron.spwlyrics.application.LyricsLoadCoordinator
import dev.gaboron.spwlyrics.application.LyricsResolver
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.AmllProvider
import dev.gaboron.spwlyrics.provider.AppleMusicProvider
import dev.gaboron.spwlyrics.provider.KugouMusicProvider
import dev.gaboron.spwlyrics.provider.LocalLyricsProvider
import dev.gaboron.spwlyrics.provider.NeteaseMusicProvider
import dev.gaboron.spwlyrics.provider.ProviderHttpClient
import dev.gaboron.spwlyrics.provider.QqMusicProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import dev.gaboron.spwlyrics.integration.manualui.ManualUiBridge
import dev.gaboron.spwlyrics.integration.manualui.ManualUiSession
import dev.gaboron.spwlyrics.integration.shortcut.ManualSearchShortcutController
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path

object PluginRuntime {
    @Volatile private var coordinator: LyricsLoadCoordinator? = null
    @Volatile private var settings: PluginSettings? = null
    @Volatile private var manualUiBridge: ManualUiBridge? = null
    @Volatile private var manualSearchShortcut: ManualSearchShortcutController? = null
    @Volatile private var cacheFolderOpener: CacheFolderOpener? = null
    private val durationProbe: TrackDurationProbe = CachedTrackDurationProbe()

    @Synchronized
    @OptIn(UnstableSpwWorkshopApi::class)
    fun install(pluginPath: String) {
        if (coordinator != null) return
        val localData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            ?.let(::Path) ?: Path(System.getProperty("user.home"))
        val root = localData.resolve("SPW Lyrics")
        val cacheDirectory = root.resolve("cache")
        val cache = FileLyricsCache(cacheDirectory)
        cacheFolderOpener = CacheFolderOpener(cacheDirectory)
        val http = ProviderHttpClient()
        val providers = listOf(
            AmllProvider(
                cacheDirectory.resolve("AMLL 索引"),
                ProviderHttpClient(requestTimeout = Duration.ofSeconds(6)),
                legacyIndexPath = root.resolve("amll").resolve("amll-index.jsonl"),
            ),
            AppleMusicProvider(http),
            QqMusicProvider(http),
            KugouMusicProvider(http),
            NeteaseMusicProvider(http),
            LocalLyricsProvider(),
        )
        coordinator = LyricsLoadCoordinator(
            cache = cache,
            resolver = LyricsResolver(providers),
            refreshBridge = ReflectiveLyricsRefreshBridge(),
            notify = ::toastWarning,
        )
        manualUiBridge = ManualUiBridge(
            pluginRoot = Path(pluginPath),
            session = ManualUiSession(
                currentQuery = ::currentQuery,
                search = ::searchManual,
                preview = ::preview,
                apply = ::applyManual,
                useLocal = ::useLocal,
                useAutomatic = ::useAutomatic,
            ),
        )
        val shortcutController = ManualSearchShortcutController(
            onPressed = ::openManualSearch,
            onFailure = {
                toastWarning("快捷键监听未启动，请在插件设置中关闭并重新开启“通过快捷键开启”后重试。")
            },
        )
        manualSearchShortcut = shortcutController
        val pluginSettings = PluginSettings(
            manager = WorkshopApi.manager.createConfigManager(),
            onManualSearchShortcutEnabledChanged = shortcutController::setEnabled,
        )
        settings = pluginSettings
        shortcutController.setEnabled(pluginSettings.manualSearchShortcutEnabled())
    }

    fun beforeLoad(mediaItem: PlaybackExtensionPoint.MediaItem): String? = load(mediaItem, LyricsLoadPhase.BEFORE_LOCAL)
    fun afterLocalLyricsMissing(mediaItem: PlaybackExtensionPoint.MediaItem): String? =
        load(mediaItem, LyricsLoadPhase.AFTER_LOCAL_MISSING)
    fun currentQuery(): TrackQuery? = coordinator?.currentQuery()
    fun searchManual(keywords: String, source: LyricsSource?) = coordinator?.searchManual(keywords, source).orEmpty()
    fun preview(candidate: LyricsCandidate) = coordinator?.preview(candidate)
    fun applyManual(candidate: LyricsCandidate): Boolean = coordinator?.applyManual(candidate) == true
    fun useLocal(): Boolean = coordinator?.useLocal() == true
    fun useAutomatic(): Boolean = coordinator?.useAutomatic() == true
    @Synchronized
    fun openManualSearch() {
        val bridge = manualUiBridge ?: return
        if (!bridge.open()) ManualSearchWindow.open()
    }
    fun openCacheFolder() {
        if (cacheFolderOpener?.open() != true) toastWarning("无法打开 SPW Lyrics 本地缓存文件夹。")
    }

    @Synchronized
    fun close() {
        manualSearchShortcut?.close()
        manualSearchShortcut = null
        manualUiBridge?.close()
        manualUiBridge = null
        cacheFolderOpener = null
        settings?.close()
        settings = null
        coordinator?.close()
        coordinator = null
    }

    private fun load(mediaItem: PlaybackExtensionPoint.MediaItem, phase: LyricsLoadPhase): String? =
        coordinator?.onLoad(
            mediaItem.toQuery(),
            phase,
            settings?.automaticReplacementPolicy() ?: AutomaticReplacementPolicy.ALWAYS,
        )

    private fun PlaybackExtensionPoint.MediaItem.toQuery() = TrackQuery(
        title = title,
        artists = TrackQuery.splitArtists(artist),
        album = album,
        albumArtists = TrackQuery.splitArtists(albumArtist),
        path = path,
        durationMs = durationProbe.durationMs(path),
    )

    private fun toastWarning(message: String) {
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
}
