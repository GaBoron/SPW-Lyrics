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
import dev.gaboron.spwlyrics.application.LyricsPrecache
import dev.gaboron.spwlyrics.application.LyricsOutputRouter
import dev.gaboron.spwlyrics.codec.MetadataLyricsFormat
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import dev.gaboron.spwlyrics.provider.AmllProvider
import dev.gaboron.spwlyrics.provider.KugouMusicProvider
import dev.gaboron.spwlyrics.provider.LocalLyricsProvider
import dev.gaboron.spwlyrics.provider.NeteaseMusicProvider
import dev.gaboron.spwlyrics.provider.ProviderHttpClient
import dev.gaboron.spwlyrics.provider.QqMusicProvider
import dev.gaboron.spwlyrics.provider.SodaMusicProvider
import dev.gaboron.spwlyrics.provider.AppleMusicProvider
import dev.gaboron.spwlyrics.provider.LrcShareProvider
import dev.gaboron.spwlyrics.provider.MusicBrainzProvider
import dev.gaboron.spwlyrics.storage.FileLyricsCache
import dev.gaboron.spwlyrics.integration.manualui.ManualSearchOperations
import dev.gaboron.spwlyrics.integration.metadataui.MetadataEditorOperations
import dev.gaboron.spwlyrics.integration.metadataui.MetadataFilePicker
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspace
import dev.gaboron.spwlyrics.integration.workspace.LyricsWorkspaceSession
import dev.gaboron.spwlyrics.integration.workspace.LyricsToolPage
import dev.gaboron.spwlyrics.integration.hostui.HostUiIntegration
import dev.gaboron.spwlyrics.storage.FileLyricsDelaySettings
import dev.gaboron.spwlyrics.storage.FileTimingCalibrationStore
import dev.gaboron.spwlyrics.storage.LyricsUiStateStore
import java.time.Duration
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance
import dev.gaboron.spwlyrics.diagnostics.PluginPerformance.Operation
import java.util.concurrent.Executors
import kotlin.io.path.Path

object PluginRuntime {
    @Volatile private var session: PluginRuntimeSession? = null

    @Synchronized
    fun install(pluginId: String, channel: com.xuncorp.spw.workshop.api.Channel) {
        if (session != null) return
        val started = System.nanoTime()
        val next = PluginRuntimeSession()
        try {
            next.install(pluginId, channel)
            session = next
        } catch (error: Throwable) {
            runCatching { next.close() }.onFailure(error::addSuppressed)
            throw error
        } finally {
            next.performance.record(Operation.START, System.nanoTime() - started)
            next.performance.report("start")
        }
    }

    @Synchronized
    fun close() {
        val previous = session ?: return
        session = null
        try { previous.performance.measure(Operation.STOP) { previous.close() } }
        finally { previous.performance.report("stop") }
    }

    fun beforeLoad(item: PlaybackExtensionPoint.MediaItem) = session?.beforeLoad(item)
    fun afterLocalLyricsMissing(item: PlaybackExtensionPoint.MediaItem) = session?.afterLocalLyricsMissing(item)
    fun currentQuery() = session?.currentQuery()
    fun openManualSearch() { session?.openManualSearch() }
    fun openBatchProcessing() { session?.openBatchProcessing() }
    fun openMetadataEditor() { session?.openMetadataEditor() }
    fun openWorkspace() { session?.openWorkspace() }
    fun timingSeeked() { session?.timingSeeked() }
    fun openCacheFolder() { session?.openCacheFolder() }
    fun resetLyricsOrder() { session?.resetLyricsOrder() }
}

/** Callbacks capture this enable's objects, never a later enable's global state. */
private class PluginRuntimeSession : AutoCloseable {
    val performance = PluginPerformance()
    @Volatile private var closed = false
    private val maintenance = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "spw-lyrics-maintenance").apply { isDaemon = true }
    }
    @Volatile private var coordinator: LyricsLoadCoordinator? = null
    @Volatile private var batchProcessor: LyricsBatchProcessor? = null
    @Volatile private var precache: LyricsPrecache? = null
    @Volatile private var workspace: LyricsWorkspace? = null
    @Volatile private var timingPlayback: SpwTimingPlayback? = null
    @Volatile private var timingMedia: TimingMediaRuntime? = null
    @Volatile private var hostUi: HostUiIntegration? = null
    @Volatile private var settings: PluginSettings? = null
    @Volatile private var cacheFolderOpener: CacheFolderOpener? = null
    private val libraryTracks = SpwLibraryTrackSource()
    private val localTtmlLyrics = LocalTtmlLyricsLoader()

    @Synchronized
    @OptIn(UnstableSpwWorkshopApi::class)
    fun install(pluginId: String, channel: com.xuncorp.spw.workshop.api.Channel) {
        if (coordinator != null) return
        val localData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            ?.let(::Path) ?: Path(System.getProperty("user.home"))
        val root = localData.resolve("SPW Lyrics")
        val cacheDirectory = root.resolve("cache")
        val (cache, delays) = performance.measure(Operation.STORAGE) {
            FileLyricsCache(cacheDirectory) to FileLyricsDelaySettings(root.resolve("歌词延迟"))
        }
        val uiState by lazy { LyricsUiStateStore(root.resolve("界面状态.properties")) }
        cacheFolderOpener = CacheFolderOpener(cacheDirectory)
        settings = performance.measure(Operation.CONFIG) { PluginSettings(WorkshopApi.manager.createConfigManager(), {
            if (closed) return@PluginSettings
            coordinator?.selectionChanged()
            coordinator?.currentQuery()?.let { precache?.trackChanged(it, force = true) }
        }, ::toastWarning, performance) }
        val http = performance.measure(Operation.HTTP) { ProviderHttpClient() }
        val providers = performance.measure(Operation.PROVIDERS) {
            listOf(
                performance.measure(Operation.AMLL) {
                    AmllProvider(
                        cacheDirectory.resolve("AMLL 索引"),
                        ProviderHttpClient(requestTimeout = Duration.ofSeconds(6)),
                        legacyIndexPath = root.resolve("amll").resolve("amll-index.jsonl"),
                        indexHttp = ProviderHttpClient(connectTimeout = Duration.ofSeconds(5), requestTimeout = Duration.ofSeconds(30)),
                    )
                },
                performance.measure(Operation.QQ) { QqMusicProvider(http) },
                performance.measure(Operation.KUGOU) { KugouMusicProvider(http) },
                performance.measure(Operation.NETEASE) { NeteaseMusicProvider(http) },
                performance.measure(Operation.SODA) { SodaMusicProvider(http) },
                performance.measure(Operation.APPLE) { AppleMusicProvider(http) },
                performance.measure(Operation.LRCSHARE) { LrcShareProvider(http) { settings?.lrcShareApiKey().orEmpty() } },
                performance.measure(Operation.MUSICBRAINZ) { MusicBrainzProvider(http) },
                performance.measure(Operation.LOCAL) { LocalLyricsProvider() },
            )
        }
        val resolver = performance.measure(Operation.RESOLVER) {
            LyricsResolver(providers, preferences = { settings?.lyricsPreferences() ?: dev.gaboron.spwlyrics.domain.LyricsPreferences() })
        }
        val refreshBridge = ReflectiveLyricsRefreshBridge()
        val output = LyricsOutputRouter(
            receiver = JmxTtmlReceiver(),
            isCurrent = { query ->
                WorkshopApi.playback.getCurrentMediaItem()?.let { item ->
                    item.path == query.path && (query.trackId.isBlank() || item.id.isBlank() || query.trackId == item.id)
                } == true
            },
            refresh = { refreshBridge.reloadCurrentLyrics() },
        )
        coordinator = performance.measure(Operation.COORDINATOR) { LyricsLoadCoordinator(
            cache = cache,
            resolver = resolver,
            refreshBridge = refreshBridge,
            notify = ::toastWarning,
            delays = delays,
            output = output,
            prepareQuery = libraryTracks::prepare,
            performance = performance,
        ) }
        precache = LyricsPrecache(
            loadLibrary = libraryTracks::loadForPrecache,
            prepare = { query ->
                coordinator?.precache(query, hasLocalLyrics = localTtmlLyrics.load(query.path) != null)
            },
            enabled = { settings?.automaticReplacementPolicy() == AutomaticReplacementPolicy.ALWAYS },
            performance = performance,
        )
        val batch = LyricsBatchProcessor(
            loadLibrary = libraryTracks::load,
            cache = cache,
            resolver = resolver,
        )
        batchProcessor = batch
        val describeBatchFailure: (Throwable) -> String = { error ->
            if (error is PluginPermissionDeniedException) "请在 SPW 的插件权限中授予曲库读取权限后重试。"
            else "批量处理失败：${error.message ?: "未知错误"}"
        }
        val metadataOperations = object : MetadataEditorOperations {
            override fun lyricsFormat() = MetadataLyricsFormat.fromStorageValue(uiState.lyricsFormat())
            override fun saveLyricsFormat(format: MetadataLyricsFormat) = uiState.rememberLyricsFormat(format.storageValue)
            override fun currentAudioPath() = currentQuery()?.path
            override fun chooseAudioFile() = MetadataFilePicker.audio()
            override fun chooseLyricsFile() = MetadataFilePicker.lyrics()
            override fun chooseCoverFile() = MetadataFilePicker.cover()
            override fun openSearch(query: TrackQuery) { workspace?.searchMetadata(query) }
            override fun afterSave(path: java.nio.file.Path) {
                val currentPath = WorkshopApi.playback.getCurrentMediaItem()?.path
                if (currentPath != null && runCatching { java.nio.file.Path.of(currentPath).toRealPath() == path }.getOrDefault(false)) {
                    check(refreshBridge.reloadCurrentLyrics()) { "请切换歌曲后重新播放以加载内嵌歌词。" }
                }
            }
        }
        val manualOperations = object : ManualSearchOperations {
            override fun candidateSort() = uiState.candidateSort()
            override fun saveCandidateSort(value: String) = uiState.rememberCandidateSort(value)
            override fun currentQuery() = this@PluginRuntimeSession.currentQuery()
            override fun search(query: TrackQuery, keywords: String, source: LyricsSource?, onStatus: (ProviderSearchStatus) -> Unit) =
                checkNotNull(coordinator) { "歌词搜索尚未启用。" }.searchManual(query, keywords, source, onStatus)
            override fun preview(candidate: LyricsCandidate) = coordinator?.preview(candidate)
            override fun automatic(query: TrackQuery) = coordinator?.previewAutomatic(query)
            override fun inspect(candidates: List<LyricsCandidate>, report: (LyricsCandidate) -> Unit) = resolver.inspectManual(candidates, report)
            override fun apply(query: TrackQuery, candidate: LyricsCandidate) =
                isCurrentTrack(query) && coordinator?.applyManual(query, candidate) == true
            override fun useLocal(query: TrackQuery) = isCurrentTrack(query) && coordinator?.useLocal(query) == true
            override fun useAutomatic(query: TrackQuery) = isCurrentTrack(query) && coordinator?.useAutomatic(query) == true
            override fun delay(query: TrackQuery) = delays.get(query)
            override fun setDelay(query: TrackQuery, delayMs: Int) =
                isCurrentTrack(query) && coordinator?.setDelay(query, delayMs) == true
            override fun metadata(candidate: LyricsCandidate) = resolver.metadata(candidate)
            override fun editMetadata(query: TrackQuery, candidate: LyricsCandidate, document: LyricsDocument?, preserveDraft: Boolean) {
                workspace?.importSearch(query, candidate, document, preserveDraft)
            }
        }
        val timingTransport = SpwTimingPlayback().also { timingPlayback = it }
        val timingRuntime = TimingMediaRuntime(pluginId, performance).also { timingMedia = it }
        val calibration by lazy { FileTimingCalibrationStore(root.resolve("打轴输入校准.properties")) }
        workspace = performance.measure(Operation.WORKSPACE) {
            LyricsWorkspace({ LyricsWorkspaceSession(manualOperations, metadataOperations, batch, describeBatchFailure,
                timingTransport, calibration, SpwTimingAudioDecoder(timingRuntime, TimingRuntimeInstall(channel), performance)) }, ::toastWarning)
        }
        hostUi = performance.measure(Operation.HOST_START) {
            HostUiIntegration(checkNotNull(workspace), ::toastWarning, performance).also { it.start() }
        }
        maintenance.execute {
            if (!closed) runCatching {
                performance.measure(Operation.STORAGE_PREPARE) { cache.prepare() }
            }.onFailure {
                System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "缓存目录准备失败，将在读写时重试", it)
            }
        }
        maintenance.scheduleWithFixedDelay({ if (!closed) performance.report("running") }, 60, 60, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun beforeLoad(mediaItem: PlaybackExtensionPoint.MediaItem): String? = performance.measure(Operation.BEFORE_LOAD) {
        load(mediaItem, LyricsLoadPhase.BEFORE_LOCAL) {
            localTtmlLyrics.load(mediaItem.path)
        }
    }
    fun afterLocalLyricsMissing(mediaItem: PlaybackExtensionPoint.MediaItem): String? = performance.measure(Operation.AFTER_MISSING) {
        load(mediaItem, LyricsLoadPhase.AFTER_LOCAL_MISSING)
    }
    fun currentQuery(): TrackQuery? = runCatching { libraryTracks.current() }.getOrElse { error ->
        toastWarning(if (error is PluginPermissionDeniedException) {
            "请在 SPW 的插件权限中授予曲库读取权限。"
        } else {
            "读取当前歌曲失败：${error.message ?: "未知错误"}"
        })
        null
    }
    @Synchronized
    fun openManualSearch() {
        workspace?.open(LyricsToolPage.SEARCH)
    }
    @Synchronized
    fun openBatchProcessing() {
        workspace?.open(LyricsToolPage.BATCH)
    }
    @Synchronized
    fun openMetadataEditor() {
        workspace?.open(LyricsToolPage.METADATA)
    }
    fun openWorkspace() = workspace?.open(LyricsToolPage.BATCH)
    fun timingSeeked() { timingPlayback?.seeked() }
    fun openCacheFolder() {
        if (cacheFolderOpener?.open() != true) toastWarning("无法打开 SPW Lyrics 本地缓存文件夹。")
    }
    fun resetLyricsOrder() {
        toastWarning(if (settings?.resetSelection() == true) "已恢复默认歌词排序。关闭并重新打开设置可查看默认顺序。" else "重置未完成，请重新打开设置后重试。")
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        maintenance.shutdownNow()
        val resources = listOfNotNull(hostUi, workspace, timingMedia, settings, batchProcessor, precache, coordinator)
        hostUi = null
        workspace = null
        timingMedia = null
        timingPlayback = null
        cacheFolderOpener = null
        settings = null
        batchProcessor = null
        precache = null
        coordinator = null
        resources.forEach { resource ->
            runCatching { resource.close() }.onFailure {
                System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "停用歌词模块失败：${resource.javaClass.simpleName}", it)
            }
        }
        libraryTracks.clear()
    }

    private fun load(
        mediaItem: PlaybackExtensionPoint.MediaItem,
        phase: LyricsLoadPhase,
        localTtml: (() -> LyricsDocument?)? = null,
    ): String? {
        if (closed) return null
        val query = libraryTracks.fromLyricsCallback(mediaItem)
        val result = coordinator?.onLoad(
            query,
            phase,
            settings?.automaticReplacementPolicy() ?: AutomaticReplacementPolicy.ALWAYS,
            localTtml,
        )
        precache?.trackChanged(query)
        return result
    }

    private fun isCurrentTrack(query: TrackQuery): Boolean = currentQuery()?.key == query.key

    private fun toastWarning(message: String) {
        if (closed) return
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
}
