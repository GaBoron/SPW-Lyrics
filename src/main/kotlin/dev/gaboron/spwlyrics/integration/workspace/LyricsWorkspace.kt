package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.awt.EventQueue

/** Owns the host page session. Hook integration supplies readiness and navigation activation. */
internal class LyricsWorkspace(private val createSession: () -> LyricsWorkspaceSession,
    private val onFailure: (String) -> Unit) : AutoCloseable {
    var session by mutableStateOf<LyricsWorkspaceSession?>(null)
        private set
    var available = false
    var beforeOpen: () -> Unit = {}
    private var stopped = false

    fun open(page: LyricsToolPage? = null) = onEdt {
        if (stopped) return@onEdt
        if (!available) {
            onFailure("歌词管理主窗口入口尚未启用，请检查类变换权限及插件启动提示。")
            return@onEdt
        }
        if (runCatching(beforeOpen).onFailure {
            onFailure("歌词管理页面未能打开：${it.cause?.message ?: it.message}")
        }.isFailure) return@onEdt
        val active = session ?: createSession().also { session = it }
        active.select(page ?: active.page)
    }

    fun importSearch(query: TrackQuery, document: LyricsDocument) = onEdt {
        open()
        session?.let { active ->
            active.select(LyricsToolPage.METADATA, initialize = false)
            if (active.metadata.state.busy) onFailure("元数据正在读写，请完成后重试导入。")
            else active.metadata.importSearch(query, document)
        }
    }

    fun requestLeave(action: () -> Unit): Boolean {
        val active = session ?: return false
        active.requestClose { dismiss(); action() }
        return true
    }

    fun unavailable() = onEdt {
        available = false
        beforeOpen = {}
        dismiss()
    }

    fun dismiss() {
        session?.close(); session = null
    }

    override fun close() = onEdt { stopped = true; dismiss() }
    private fun onEdt(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }
}
