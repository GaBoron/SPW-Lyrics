package dev.gaboron.spwlyrics.integration

import java.net.URI
import java.nio.file.Path

/** Host callbacks can expose network/virtual media as well as filesystem paths. */
internal fun localMediaPath(value: String): Path? {
    if (value.isBlank()) return null
    val windowsDrive = value.length >= 3 && value[0].isLetter() && value[1] == ':' && value[2] in "\\/"
    if (!windowsDrive && MEDIA_SCHEME.containsMatchIn(value)) {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        return runCatching { Path.of(uri) }.getOrNull()
    }
    return runCatching { Path.of(value) }.getOrNull()
}

private val MEDIA_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
