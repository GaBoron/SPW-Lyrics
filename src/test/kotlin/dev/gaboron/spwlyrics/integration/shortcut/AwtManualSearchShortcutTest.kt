package dev.gaboron.spwlyrics.integration.shortcut

import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwtManualSearchShortcutTest {
    private val source = object : Component() { }
    private val modifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK

    @Test
    fun `consumes the full shortcut sequence and opens once after release`() {
        val opened = CountDownLatch(1)
        val count = AtomicInteger()
        val shortcut = AwtManualSearchShortcut {
            count.incrementAndGet()
            opened.countDown()
        }

        try {
            assertTrue(shortcut.dispatch(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_M, 'M')))
            assertTrue(shortcut.dispatch(event(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'm')))
            assertTrue(shortcut.dispatch(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_M, 'M')))
            assertTrue(shortcut.dispatch(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_M, 'M')))
            assertTrue(opened.await(1, TimeUnit.SECONDS))
            assertEquals(1, count.get())
        } finally {
            shortcut.close()
        }
    }

    @Test
    fun `rejects a shortcut with alt graph`() {
        val count = AtomicInteger()
        val shortcut = AwtManualSearchShortcut { count.incrementAndGet() }

        try {
            val altGraphModifiers = modifiers or InputEvent.ALT_GRAPH_DOWN_MASK
            assertFalse(shortcut.dispatch(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_M, 'M', altGraphModifiers)))
            assertFalse(shortcut.dispatch(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_M, 'M', altGraphModifiers)))
            assertEquals(0, count.get())
        } finally {
            shortcut.close()
        }
    }

    private fun event(id: Int, keyCode: Int, keyChar: Char, eventModifiers: Int = modifiers) =
        KeyEvent(source, id, System.currentTimeMillis(), eventModifiers, keyCode, keyChar)
}
