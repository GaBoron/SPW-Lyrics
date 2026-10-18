package dev.gaboron.spwlyrics.integration.composeui

import org.jetbrains.skiko.Library

/** Share SPW's Compose/Skiko classes and let the host select its platform-native library. */
internal object ComposeDesktopRuntime {
    fun load() {
        Library.load()
    }
}
