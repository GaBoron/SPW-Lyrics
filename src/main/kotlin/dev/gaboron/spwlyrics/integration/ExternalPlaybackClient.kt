package dev.gaboron.spwlyrics.integration

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import javax.management.openmbean.CompositeData

/** JDK-only process-local contract, shared by independently loaded playback plugins. */
internal class ExternalPlaybackClient {
    private val server = ManagementFactory.getPlatformMBeanServer()

    fun snapshot(): ExternalPlayback? = runCatching {
        if (!server.isRegistered(NAME) || server.getAttribute(NAME, "ProtocolVersion") != 1) return null
        val value = server.getAttribute(NAME, "Snapshot") as? CompositeData ?: return null
        if (value["active"] != true) return null
        ExternalPlayback(
            provider = value.text("provider"), sessionId = value.text("sessionId"),
            revision = value.number("revision"), trackRevision = value.number("trackRevision"),
            uri = value.text("uri"), title = value.text("title"), artist = value.text("artist"),
            album = value.text("album"), coverUrl = value.text("coverUrl"),
            durationMs = value.number("durationMs").coerceAtLeast(0),
            positionMs = value.number("positionMs").coerceAtLeast(0),
            sampledAtNanos = value.number("sampledAtNanos"), playing = value["playing"] == true,
            volumePercent = value.number("volumePercent").toInt().coerceIn(0, 100),
            playbackSpeed = (if (value.containsKey("playbackSpeed")) value["playbackSpeed"] as? Number else null)
                ?.toDouble()?.takeIf { it.isFinite() && it in .1..8.0 } ?: 1.0,
        ).takeIf { it.provider.isNotBlank() && it.sessionId.isNotBlank() && it.uri.isNotBlank() }
    }.getOrNull()

    fun submit(track: ExternalPlayback, format: String, content: String, spl: String, offsetMs: Long): Boolean = runCatching {
        server.invoke(NAME, "submitLyrics", arrayOf<Any>(track.sessionId, track.trackRevision, track.uri, format, content, spl, offsetMs),
            arrayOf("java.lang.String", "long", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "long")) == true
    }.getOrDefault(false)

    fun clear(track: ExternalPlayback): Boolean = runCatching {
        server.invoke(NAME, "clearLyrics", arrayOf<Any>(track.sessionId, track.trackRevision, track.uri),
            arrayOf("java.lang.String", "long", "java.lang.String")) == true
    }.getOrDefault(false)

    private companion object {
        val NAME = ObjectName("dev.gaboron.spw:type=ExternalPlayback,version=1")
    }
}

internal data class ExternalPlayback(
    val provider: String, val sessionId: String, val revision: Long, val trackRevision: Long,
    val uri: String, val title: String, val artist: String, val album: String, val coverUrl: String,
    val durationMs: Long, val positionMs: Long, val sampledAtNanos: Long, val playing: Boolean,
    val volumePercent: Int, val playbackSpeed: Double,
) {
    val identity: String get() = "$sessionId|$trackRevision|$uri"

    fun currentPosition(): Long {
        val elapsed = if (playing) ((System.nanoTime() - sampledAtNanos) / 1_000_000L).coerceAtLeast(0) else 0
        val position = positionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }
}

private fun CompositeData.text(key: String): String = get(key) as? String ?: ""
private fun CompositeData.number(key: String): Long = if (containsKey(key)) (get(key) as? Number)?.toLong() ?: 0 else 0
