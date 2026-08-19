package dev.gaboron.spwlyrics.integration.shortcut

import java.awt.EventQueue
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class AwtManualSearchShortcut(
    private val onPressed: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val detector = ShortcutPressDetector()
    private val actions = Executors.newSingleThreadExecutor { task ->
        Thread(task, "spw-lyrics-manual-search-action").apply { isDaemon = true }
    }
    private val dispatcher = KeyEventDispatcher(::dispatch)
    private var shortcutSequenceActive = false

    private fun register() = onEventDispatchThread {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    }

    internal fun dispatch(event: KeyEvent): Boolean {
        if (closed.get() || event.isConsumed) return false
        if (event.id == KeyEvent.KEY_TYPED) return shortcutSequenceActive
        if (event.keyCode != KeyEvent.VK_M) return false
        if (event.id == KeyEvent.KEY_RELEASED) {
            val shouldOpen = shortcutSequenceActive
            shortcutSequenceActive = false
            detector.update(
                ShortcutKeyState(
                    targetApplicationForeground = true,
                    controlDown = false,
                    shiftDown = false,
                    letterDown = false,
                ),
            )
            if (shouldOpen) submitOpenAction()
            return shouldOpen
        }
        if (event.id != KeyEvent.KEY_PRESSED) return false
        if (shortcutSequenceActive) return true
        val exactModifiers = event.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0 &&
            event.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0 &&
            event.modifiersEx and InputEvent.ALT_DOWN_MASK == 0 &&
            event.modifiersEx and InputEvent.META_DOWN_MASK == 0 &&
            event.modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK == 0
        val shouldTrigger = detector.update(
            ShortcutKeyState(
                targetApplicationForeground = true,
                controlDown = event.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0,
                shiftDown = event.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0,
                altDown = event.modifiersEx and InputEvent.ALT_DOWN_MASK != 0,
                metaDown = event.modifiersEx and InputEvent.META_DOWN_MASK != 0,
                letterDown = exactModifiers,
            ),
        )
        if (shouldTrigger) {
            shortcutSequenceActive = true
        }
        return exactModifiers
    }

    private fun submitOpenAction() {
        actions.execute {
            if (!closed.get()) runCatching(onPressed)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onEventDispatchThread {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
        actions.shutdownNow()
    }

    companion object {
        fun start(onPressed: () -> Unit, onFailure: (Throwable) -> Unit): AutoCloseable {
            val shortcut = AwtManualSearchShortcut(onPressed)
            return runCatching {
                shortcut.register()
                shortcut
            }.getOrElse { error ->
                shortcut.closed.set(true)
                shortcut.actions.shutdownNow()
                onFailure(error)
                AutoCloseable { }
            }
        }

        private fun onEventDispatchThread(action: () -> Unit) {
            if (EventQueue.isDispatchThread()) action() else EventQueue.invokeAndWait(action)
        }
    }
}
