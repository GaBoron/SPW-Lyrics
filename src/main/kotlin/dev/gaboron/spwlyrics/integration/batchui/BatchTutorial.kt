package dev.gaboron.spwlyrics.integration.batchui

import dev.gaboron.spwlyrics.application.*
import dev.gaboron.spwlyrics.integration.metadataui.MetadataTutorialDemo
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialSong
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialStep
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialTarget

internal enum class BatchTutorialTarget : TutorialTarget { REFRESH, TRACKS, REPROCESS, CACHE, WRITE, OPTIONS, EDIT, PROGRESS, CONTROL, RETRY }
internal enum class BatchTutorialScene { READY, RUNNING, RESULTS }

internal enum class BatchTutorialStep(
    override val title: String,
    override val detail: String,
    override val target: BatchTutorialTarget,
    val scene: BatchTutorialScene = BatchTutorialScene.READY,
) : TutorialStep {
    LIBRARY("先读取音乐库", "这里管理 SPW 曲库里的歌曲。扫描曲库后点击“刷新音乐库”，或按 F5。\n\n现在展示的是虚拟队列，不会读取真实曲库、联网、缓存或写入歌曲。完成后回到实际任务与选择。", BatchTutorialTarget.REFRESH),
    SELECT("勾选要处理的歌曲", "点击行或左侧勾选框选择歌曲，上方会显示已选数量。“全选 / 取消全选”可批量调整；列表中用方向键定位，Space 切换勾选。\n\n开始任务前先检查选择范围。运行期间不能改选。", BatchTutorialTarget.TRACKS),
    REPROCESS("决定是否重建已有缓存", "默认保留有效缓存。“重新处理已有缓存”开启后，下一次缓存任务会重新处理这些歌曲。\n\n它控制缓存任务是否重做；批量写入哪些标签、是否覆盖已有值，在后面的写入选项里决定。", BatchTutorialTarget.REPROCESS),
    CACHE("只想提前准备歌词时", "勾选歌曲后点“缓存歌词”。插件会匹配歌词并写入自身缓存，不修改歌曲文件；下次播放可直接使用。\n\n自动匹配不可靠、已有单曲手动选择或本地锁定的歌曲会按规则跳过。", BatchTutorialTarget.CACHE),
    WRITE("需要写入歌曲文件时", "点“批量写入”打开选项。先选要写入的标签、封面或 TTML 歌词，再决定只补空项还是覆盖所选数据。\n\n打开选项还不会修改文件，确认“开始写入”后才执行。", BatchTutorialTarget.WRITE),
    OPTIONS("勾选字段并选择替换方式", "这是写入选项的示例。点击每个勾选框选择字段、封面或内嵌歌词；点击方式菜单切换“仅补充空数据 / 覆盖所选数据”。\n\n在线空值不会清除已有内容。核对歌曲数量与选项后，点“开始写入”；“取消”返回队列。", BatchTutorialTarget.OPTIONS),
    EDIT("单独修正一首歌", "每首歌右侧的铅笔可打开歌曲信息编辑。这里展示已展开的虚拟歌曲：可修改标签、替换封面，再点“写入歌曲文件”。\n\n保存会写入这一首歌曲；开始批量任务前，先处理未保存的逐曲修改。窄窗口可点右上角关闭编辑的图标回到列表。", BatchTutorialTarget.EDIT),
    PROGRESS("查看进度与每曲结果", "进度区显示已处理数量、新缓存、已有缓存及失败数，列表里可看每首歌的处理阶段和原因。最多同时处理 3 首。\n\n离开页面或关闭管理页后，真实任务仍会继续；教程中的处理状态仅为示例。", BatchTutorialTarget.PROGRESS, BatchTutorialScene.RUNNING),
    CONTROL("暂停、继续或停止", "运行时点击“暂停”，暂缓开始新的歌曲；之后点“继续处理”恢复。“停止”保留已完成结果，已开始保存的歌曲会完成当前写入。\n\n暂停或停止不会撤销已经写入的缓存和标签。", BatchTutorialTarget.CONTROL, BatchTutorialScene.RUNNING),
    RETRY("失败时先看原因再重试", "任务结束后，先查看失败行的具体原因，排除网络或文件权限问题，再点“重试失败项”。它只重新处理失败的歌曲。\n\n示例队列含一个虚拟失败项，不会对真实歌曲执行重试。", BatchTutorialTarget.RETRY, BatchTutorialScene.RESULTS),
    FINISH("写入完成后更新曲库", "缓存任务完成后可直接播放。批量写入标签或封面后，请重新扫描 SPW 曲库，再切歌核对；随后可点“刷新音乐库”更新队列。\n\n教程结束后回到真实页面。配置中只有一个“重置全部教程”入口，可同时重看三页教程。", BatchTutorialTarget.REFRESH),
}

/** Static queue scenes use no processor; demonstration commands cannot reach files or providers. */
internal class BatchTutorialDemo : AutoCloseable {
    val metadata = MetadataTutorialDemo()
    private val queries = listOf(TutorialSong.query, TutorialSong.query.copy(title = "夜空练习", path = "教程示例/夜空练习.flac"),
        TutorialSong.query.copy(title = "下一站晴天", path = "教程示例/下一站晴天.flac"),
        TutorialSong.query.copy(title = "给明天的歌", path = "教程示例/给明天的歌.flac"))
    private val keys = queries.map { it.key }

    fun controller(scene: BatchTutorialScene): BatchProcessingActions = object : BatchProcessingActions {
        private val items = queries.mapIndexed { index, query ->
            val status = if (scene == BatchTutorialScene.READY) LyricsBatchItemState.WAITING else when (index) {
                0 -> LyricsBatchItemState.COMPLETED
                1 -> LyricsBatchItemState.CACHED
                2 -> if (scene == BatchTutorialScene.RUNNING) LyricsBatchItemState.SEARCHING else LyricsBatchItemState.COMPLETED
                else -> LyricsBatchItemState.FAILED
            }
            LyricsBatchItem(query, status, progress = if (status == LyricsBatchItemState.WAITING) 0.0 else if (status == LyricsBatchItemState.SEARCHING) .55 else 1.0,
                source = "示例来源", quality = "逐字", message = if (status == LyricsBatchItemState.FAILED) "示例：请求超时，请检查网络后重试" else "")
        }
        override val state = BatchProcessingState(snapshot = LyricsBatchSnapshot(when (scene) {
            BatchTutorialScene.READY -> LyricsBatchState.IDLE
            BatchTutorialScene.RUNNING -> LyricsBatchState.RUNNING
            BatchTutorialScene.RESULTS -> LyricsBatchState.COMPLETED
        }, items), keys = keys, selectedKeys = keys.toSet(), busy = false, message = "教程虚拟队列 · 不会启动真实任务。")
        override fun refresh() = Unit
        override fun includeCached(value: Boolean) = Unit
        override fun select(key: String) = Unit
        override fun selectAll(value: Boolean) = Unit
        override fun start(metadata: BatchMetadataOptions?) = Unit
        override fun pauseOrResume() = Unit
        override fun stop() = Unit
        override fun retry() = Unit
    }

    override fun close() = metadata.close()
}
