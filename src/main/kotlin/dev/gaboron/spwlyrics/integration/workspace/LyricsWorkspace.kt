package dev.gaboron.spwlyrics.integration.workspace

import androidx.compose.runtime.*
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.integration.composeui.DesktopToolWindow
import dev.gaboron.spwlyrics.integration.composeui.DesktopWindowContent
import java.awt.EventQueue

/** Owns one session and routes it to exactly one view. Hook integration supplies only a host port. */
internal class LyricsWorkspace(private val createSession: () -> LyricsWorkspaceSession,
    private val onFailure: (String) -> Unit) : AutoCloseable {
    var embeddedSession by mutableStateOf<LyricsWorkspaceSession?>(null)
        private set
    var embeddedAvailable = false
    var beforeEmbed: () -> Unit = {}
    private var session: LyricsWorkspaceSession? = null
    private var window: DesktopToolWindow? = null
    private var stopped = false

    fun open(page: LyricsToolPage? = null, standalone: Boolean = false) = onEdt {
        if (stopped) return@onEdt
        val active = session ?: createSession().also { session = it }
        page?.let { active.select(it) }
        if (!standalone && embeddedAvailable) {
            if (embeddedSession == null) beforeEmbed()
            window?.close(); window = null
            embeddedSession = active
        } else {
            embeddedSession = null
            val shown = window ?: DesktopToolWindow("SPW Lyrics · 歌词管理", "lyrics-workspace-position.json", onFailure) {
                DesktopWindowContent({ LyricsWorkspaceContent(active, false, {}, ::dismiss) }, {},
                    { dispose -> active.requestClose { dismiss(); dispose() } })
            }.also { window = it }
            shown.open()
        }
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
        val active = embeddedSession ?: return false
        active.requestClose { dismiss(); action() }
        return true
    }

    fun unavailable() = onEdt {
        embeddedAvailable = false
        beforeEmbed = {}
        if (embeddedSession != null) open(standalone = true)
    }

    private fun dismiss() {
        embeddedSession = null
        window?.close(); window = null
        session?.close(); session = null
    }

    override fun close() = onEdt { stopped = true; dismiss() }
    private fun onEdt(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }
}
