package dev.gaboron.spwlyrics.integration.shortcut

internal data class ShortcutKeyState(
    val targetApplicationForeground: Boolean,
    val controlDown: Boolean,
    val shiftDown: Boolean,
    val altDown: Boolean = false,
    val metaDown: Boolean = false,
    val letterDown: Boolean,
)

internal class ShortcutPressDetector {
    private var chordWasDown = false

    fun update(state: ShortcutKeyState): Boolean {
        val chordIsDown = state.controlDown && state.shiftDown && !state.altDown && !state.metaDown && state.letterDown
        val shouldTrigger = state.targetApplicationForeground && chordIsDown && !chordWasDown
        chordWasDown = chordIsDown
        return shouldTrigger
    }
}
