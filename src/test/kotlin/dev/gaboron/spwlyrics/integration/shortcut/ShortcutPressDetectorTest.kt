package dev.gaboron.spwlyrics.integration.shortcut

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortcutPressDetectorTest {
    private val detector = ShortcutPressDetector()

    @Test
    fun `triggers once when control shift m is pressed in the target application`() {
        assertTrue(detector.update(state(foreground = true, control = true, shift = true, letter = true)))
        assertFalse(detector.update(state(foreground = true, control = true, shift = true, letter = true)))
    }

    @Test
    fun `rearms after the shortcut is released`() {
        assertTrue(detector.update(state(foreground = true, control = true, shift = true, letter = true)))
        assertFalse(detector.update(state(foreground = true, control = false, shift = false, letter = false)))
        assertTrue(detector.update(state(foreground = true, control = true, shift = true, letter = true)))
    }

    @Test
    fun `does not trigger after a chord pressed in another application becomes foreground`() {
        assertFalse(detector.update(state(foreground = false, control = true, shift = true, letter = true)))
        assertFalse(detector.update(state(foreground = true, control = true, shift = true, letter = true)))
    }

    @Test
    fun `rejects additional alt or windows modifiers`() {
        assertFalse(detector.update(state(foreground = true, control = true, shift = true, alt = true, letter = true)))
        assertFalse(detector.update(state(foreground = true, control = true, shift = true, meta = true, letter = true)))
    }

    private fun state(
        foreground: Boolean,
        control: Boolean,
        shift: Boolean,
        letter: Boolean,
        alt: Boolean = false,
        meta: Boolean = false,
    ) =
        ShortcutKeyState(
            targetApplicationForeground = foreground,
            controlDown = control,
            shiftDown = shift,
            altDown = alt,
            metaDown = meta,
            letterDown = letter,
        )
}
