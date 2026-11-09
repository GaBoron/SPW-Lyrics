package dev.gaboron.spwlyrics.integration

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import javax.management.openmbean.CompositeData

/** JDK-only process-local contract, shared by independently loaded playback plugins. */
internal class ExternalPlaybackClient {
    private val server = ManagementFactory.getPlatformMBeanServer()

    fun snapshot(): ExternalPlayback? = runCatching {
        server.queryNames(SOURCES, null).mapNotNull(::read)
            .maxWithOrNull(compareBy<ExternalPlayback> { it.activatedAtNanos }.thenBy { it.sourceName.canonicalName })
    }.getOrNull()

    private fun read(name: ObjectName): ExternalPlayback? = runCatching {
        if (server.getAttribute(name, "ProtocolVersion") != 1) return null
        val value = server.getAttribute(name, "Snapshot") as? CompositeData ?: return null
        if (value["active"] != true) return null
        val updated = value.number("updatedAtNanos")
        if (updated != 0L && System.nanoTime() - updated !in 0..15_000_000_000L) return null
        ExternalPlayback(
            sourceName = name, activatedAtNanos = value.number("activatedAtNanos"),
            provider = value.text("provider"), sessionId = value.text("sessionId"),
            revision = value.number("revision"), trackRevision = value.number("trackRevision"),
            uri = value.text("uri"), title = value.text("title"), artist = value.text("artist"),
            album = value.text("album"), coverUrl = value.text("coverUrl"),
            durationMs = value.number("durationMs").coerceAtLeast(0),
            positionMs = value.number("positionMs").coerceAtLeast(0),
            sampledAtNanos = value.number("sampledAtNanos"), playing = value["playing"] == true,
            volumePercent = value.number("volumePercent").coerceIn(0, 100).toInt(),
            playbackSpeed = (if (value.containsKey("playbackSpeed")) value["playbackSpeed"] as? Number else null)
                ?.toDouble()?.takeIf { it.isFinite() && it in .1..8.0 } ?: 1.0,
        ).takeIf { it.provider.isNotBlank() && it.sessionId.isNotBlank() && it.uri.isNotBlank() }
    }.getOrNull()

    fun sourceLyrics(track: ExternalPlayback): ExternalLyrics? = runCatching {
        val value = server.getAttribute(track.sourceName, "SourceLyrics") as? CompositeData ?: return null
        if (value.text("sessionId") != track.sessionId || value.number("trackRevision") != track.trackRevision ||
            value.text("uri") != track.uri) return null
        ExternalLyrics(value.text("format"), value.text("content"), value.text("spl"),
            value.number("offsetMs"), value.number("revision"))
    }.getOrNull()

    fun submit(track: ExternalPlayback, format: String, content: String, spl: String, offsetMs: Long): Boolean = runCatching {
        if (snapshot()?.identity != track.identity) return false
        server.invoke(track.sourceName, "submitLyrics", arrayOf<Any>(track.sessionId, track.trackRevision, track.uri, format, content, spl, offsetMs),
            arrayOf("java.lang.String", "long", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "long")) == true
    }.getOrDefault(false)

    fun clear(track: ExternalPlayback): Boolean = runCatching {
        if (snapshot()?.identity != track.identity) return false
        server.invoke(track.sourceName, "clearLyrics", arrayOf<Any>(track.sessionId, track.trackRevision, track.uri),
            arrayOf("java.lang.String", "long", "java.lang.String")) == true
    }.getOrDefault(false)

    private companion object {
        val SOURCES = ObjectName("dev.gaboron.spw:type=ExternalPlayback,version=1,*")
    }
}

internal data class ExternalPlayback(
    val sourceName: ObjectName, val activatedAtNanos: Long,
    val provider: String, val sessionId: String, val revision: Long, val trackRevision: Long,
    val uri: String, val title: String, val artist: String, val album: String, val coverUrl: String,
    val durationMs: Long, val positionMs: Long, val sampledAtNanos: Long, val playing: Boolean,
    val volumePercent: Int, val playbackSpeed: Double,
) {
    val identity: String get() = listOf(sourceName.canonicalName, provider, sessionId, trackRevision.toString(), uri)
        .joinToString("") { "${it.length}:$it" }

    fun currentPosition(): Long {
        val elapsed = if (playing) ((System.nanoTime() - sampledAtNanos) / 1_000_000L).coerceAtLeast(0) else 0
        val position = (positionMs.toDouble() + elapsed * playbackSpeed).toLong()
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }
}

internal data class ExternalLyrics(val format: String, val content: String, val spl: String, val offsetMs: Long, val revision: Long)

private fun CompositeData.text(key: String): String = if (containsKey(key)) get(key) as? String ?: "" else ""
private fun CompositeData.number(key: String): Long = if (containsKey(key)) (get(key) as? Number)?.toLong() ?: 0 else 0
