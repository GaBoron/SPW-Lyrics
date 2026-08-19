package dev.gaboron.spwlyrics.integration.shortcut

import kotlin.test.Test
import kotlin.test.assertEquals

class ManualSearchShortcutControllerTest {
    @Test
    fun `registers once while enabled and closes each active registration`() {
        var starts = 0
        var closes = 0
        val controller = ManualSearchShortcutController(
            onPressed = {},
            onFailure = {},
            startShortcut = { _, _ ->
                starts++
                AutoCloseable { closes++ }
            },
        )

        controller.setEnabled(true)
        controller.setEnabled(true)
        assertEquals(1, starts)

        controller.setEnabled(false)
        controller.setEnabled(false)
        assertEquals(1, closes)

        controller.setEnabled(true)
        assertEquals(2, starts)

        controller.close()
        controller.setEnabled(true)
        assertEquals(2, starts)
        assertEquals(2, closes)
    }
}
