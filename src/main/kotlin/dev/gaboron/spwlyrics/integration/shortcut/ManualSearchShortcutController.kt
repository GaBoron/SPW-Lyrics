package dev.gaboron.spwlyrics.integration.shortcut

internal class ManualSearchShortcutController(
    private val onPressed: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val startShortcut: (onPressed: () -> Unit, onFailure: (Throwable) -> Unit) -> AutoCloseable =
        AwtManualSearchShortcut::start,
) : AutoCloseable {
    private var registration: AutoCloseable? = null
    private var closed = false

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled) {
            if (registration == null) registration = startShortcut(onPressed, onFailure)
        } else {
            registration?.close()
            registration = null
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        registration?.close()
        registration = null
    }
}
