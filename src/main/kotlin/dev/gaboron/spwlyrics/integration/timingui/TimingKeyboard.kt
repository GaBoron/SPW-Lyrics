package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.ui.input.key.*
import dev.gaboron.spwlyrics.domain.TimingMark

/** Installed only on the lyric capture area, so typing in editor/settings fields stays ordinary. */
internal class TimingKeyboard(private val controller: LyricsTimingController) {
    private val held = mutableSetOf<Key>()
    private fun mark(key: Key) = when (key) { Key.F -> TimingMark.START; Key.G -> TimingMark.CONNECT; Key.H -> TimingMark.END; else -> null }

    fun event(event: KeyEvent): Boolean {
        val key = event.key
        if (event.type == KeyEventType.KeyUp) {
            if (!held.remove(key)) return key == Key.Spacebar
            mark(key)?.let(controller::release)
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        val action: (() -> Unit)? = if (event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) when (key) {
            Key.Z -> ({ controller.undo(event.isShiftPressed) })
            Key.Y -> ({ controller.undo(true) })
            else -> null
        } else if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) null else when (key) {
            Key.F, Key.G, Key.H -> ({ controller.press(mark(key)!!) })
            Key.A -> ({ controller.move(-1) }); Key.D -> ({ controller.move(1) })
            Key.W -> ({ controller.moveLine(-1) }); Key.S -> ({ controller.moveLine(1) })
            Key.R -> ({ controller.move(-1, true) }); Key.Y -> ({ controller.move(1, true) })
            Key.MoveHome -> ({ controller.boundary(false) }); Key.MoveEnd -> ({ controller.boundary(true) })
            Key.Spacebar -> controller::playPause
            Key.DirectionLeft -> ({ controller.seek(-1000) }); Key.DirectionRight -> ({ controller.seek(1000) })
            Key.LeftBracket -> ({ controller.stepRate(-.05f) }); Key.RightBracket -> ({ controller.stepRate(.05f) })
            Key.Apostrophe -> ({ controller.rate(1f) })
            else -> null
        }
        if (action == null) return false
        if (held.add(key)) action()
        return true
    }

    fun clear() {
        held.mapNotNull(::mark).forEach { controller.cancelPress(it) }
        held.clear()
    }
}
