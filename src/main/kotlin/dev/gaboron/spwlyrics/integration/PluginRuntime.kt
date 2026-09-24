package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import dev.gaboron.spwlyrics.application.AutomaticReplacementPolicy
import dev.gaboron.spwlyrics.application.LyricsLoadPhase
import dev.gaboron.spwlyrics.application.LyricsLoadCoordinator
import dev.gaboron.spwlyrics.application.LyricsBatchProcessor
import dev.gaboron.spwlyrics.application.LyricsResolver
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.provider.AmllProvider
import dev.gaboron.spwlyrics.provider.KugouMusicProvider
import dev.gaboron.spwlyrics.provider.LocalLyricsProvider
import dev.gaboron.spwlyrics.provider.NeteaseMusicProvider
import dev.gaboron.spwlyrics.provider.ProviderHttpClient
import dev.gaboron.spwlyrics.provider.QqMusicProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import dev.gaboron.spwlyrics.integration.manualui.ManualUiBridge
import dev.gaboron.spwlyrics.integration.manualui.ManualUiSession
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path

object PluginRuntime {
    @Volatile private var coordinator: LyricsLoadCoordinator? = null
    @Volatile private var batchProcessor: LyricsBatchProcessor? = null
    @Volatile private var settings: PluginSettings? = null
    @Volatile private var manualUiBridge: ManualUiBridge? = null
    @Volatile private var manualSearchShortcut: SpwManualSearchBinding? = null
    @Volatile private var cacheFolderOpener: CacheFolderOpener? = null
    private val libraryTracks = SpwLibraryTrackSource()

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
            QqMusicProvider(http),
            KugouMusicProvider(http),
            NeteaseMusicProvider(http),
            LocalLyricsProvider(),
        )
        val resolver = LyricsResolver(providers)
        coordinator = LyricsLoadCoordinator(
            cache = cache,
            resolver = resolver,
            refreshBridge = ReflectiveLyricsRefreshBridge(),
            notify = ::toastWarning,
        )
        val batch = LyricsBatchProcessor(
            loadLibrary = libraryTracks::load,
            cache = cache,
            resolver = resolver,
        )
        batchProcessor = batch
        manualUiBridge = ManualUiBridge(
            pluginRoot = Path(pluginPath),
            session = ManualUiSession(
                currentQuery = ::currentQuery,
                search = ::searchManual,
                preview = ::preview,
                apply = ::applyManual,
                useLocal = ::useLocal,
                useAutomatic = ::useAutomatic,
                disableTranslation = ::disableTranslation,
                batchProcessor = batch,
            ),
        )
        settings = PluginSettings(WorkshopApi.manager.createConfigManager())
        val shortcut = SpwManualSearchBinding(::openManualSearch)
        manualSearchShortcut = shortcut
        runCatching { shortcut.register() }.onFailure {
            toastWarning("无法注册歌词搜索快捷键，请检查 SPW 的快捷键权限和冲突设置。")
        }.onSuccess { granted ->
            if (!granted) toastWarning("歌词搜索快捷键未启用：请在 SPW 的插件权限中授予快捷键权限。")
        }
    }

    fun beforeLoad(mediaItem: PlaybackExtensionPoint.MediaItem): String? = load(mediaItem, LyricsLoadPhase.BEFORE_LOCAL)
    fun afterLocalLyricsMissing(mediaItem: PlaybackExtensionPoint.MediaItem): String? =
        load(mediaItem, LyricsLoadPhase.AFTER_LOCAL_MISSING)
    fun currentQuery(): TrackQuery? = runCatching { libraryTracks.current() }.getOrElse { error ->
        toastWarning(if (error is PluginPermissionDeniedException) {
            "请在 SPW 的插件权限中授予曲库读取权限。"
        } else {
            "读取当前歌曲失败：${error.message ?: "未知错误"}"
        })
        null
    }
    fun searchManual(keywords: String, source: LyricsSource?) = coordinator?.searchManual(keywords, source).orEmpty()
    fun preview(candidate: LyricsCandidate) = coordinator?.preview(candidate)
    fun applyManual(candidate: LyricsCandidate): Boolean = coordinator?.applyManual(candidate) == true
    fun useLocal(): Boolean = coordinator?.useLocal() == true
    fun useAutomatic(): Boolean = coordinator?.useAutomatic() == true
    fun disableTranslation(): Boolean = coordinator?.disableSupplementalTranslation() == true
    @Synchronized
    fun openManualSearch() {
        val bridge = manualUiBridge ?: return
        if (!bridge.open("manual")) ManualSearchWindow.open()
    }
    @Synchronized
    fun openBatchProcessing() {
        val bridge = manualUiBridge ?: return
        if (!bridge.open("batch")) toastWarning("批量处理窗口未能启动，请重新安装插件后再试。")
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
        batchProcessor?.close()
        batchProcessor = null
        coordinator?.close()
        coordinator = null
        libraryTracks.clear()
    }

    private fun load(mediaItem: PlaybackExtensionPoint.MediaItem, phase: LyricsLoadPhase): String? =
        coordinator?.onLoad(
            libraryTracks.fromLyricsCallback(mediaItem),
            phase,
            settings?.automaticReplacementPolicy() ?: AutomaticReplacementPolicy.ALWAYS,
        )

    private fun toastWarning(message: String) {
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
}
