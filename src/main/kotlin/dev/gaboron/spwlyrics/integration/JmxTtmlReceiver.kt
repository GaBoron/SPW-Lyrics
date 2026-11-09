package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.NativeTtmlReceiver
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import javax.management.MBeanServer
import javax.management.MBeanServerDelegate
import javax.management.MBeanServerNotification
import javax.management.NotificationListener
import javax.management.ObjectName

/** Protocol 1 uses JDK types only, across the two plugins' isolated PF4J class loaders. */
internal class JmxTtmlReceiver(
    private val server: MBeanServer = ManagementFactory.getPlatformMBeanServer(),
    private val external: ExternalPlaybackClient = ExternalPlaybackClient(),
) : NativeTtmlReceiver {
    private val generation = AtomicLong()
    private val listener = NotificationListener { notification, _ ->
        if (notification is MBeanServerNotification && notification.mBeanName == NAME) generation.incrementAndGet()
    }
    private var listening = false

    override fun state(): Any? {
        external.snapshot()?.let { return it.identity }
        if (!listening) {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, null, null)
            listening = true
        }
        return if (server.isRegistered(NAME) && server.getAttribute(NAME, "ProtocolVersion") == 1 &&
            server.getAttribute(NAME, "Active") == true) generation.get() else null
    }

    override fun submit(query: TrackQuery, rawTtml: String, offsetMs: Long): Boolean =
        (query.path.isNotBlank() || query.trackId.isNotBlank()) && state() != null && server.invoke(
            NAME, "submitTtml", arrayOf<Any>(query.trackId, query.path, rawTtml, offsetMs),
            arrayOf("java.lang.String", "java.lang.String", "java.lang.String", "long"),
        ) == true

    override fun select(query: TrackQuery, rawTtml: String?, offsetMs: Long): Boolean {
        if ((query.path.isBlank() && query.trackId.isBlank()) || state() == null) return false
        // The optional selection operation preserves protocol-1 compatibility with older receivers.
        if (server.getMBeanInfo(NAME).operations.any { it.name == "selectLyrics" }) return server.invoke(
            NAME, "selectLyrics", arrayOf<Any>(query.trackId, query.path, rawTtml.orEmpty(), offsetMs),
            arrayOf("java.lang.String", "java.lang.String", "java.lang.String", "long"),
        ) == true
        return rawTtml != null && submit(query, rawTtml, offsetMs)
    }

    override fun clear(query: TrackQuery) {
        if (query.isExternalPlayback) {
            matchingExternal(query)?.let(external::clear)
            return
        }
        if ((query.path.isNotBlank() || query.trackId.isNotBlank()) && state() != null) server.invoke(
            NAME, "clearTtml", arrayOf(query.trackId, query.path),
            arrayOf("java.lang.String", "java.lang.String"),
        )
    }

    override fun selectDocument(query: TrackQuery, document: LyricsDocument?, offsetMs: Int): Boolean? {
        if (!query.isExternalPlayback) return null
        val track = matchingExternal(query) ?: return false
        if (document == null) return external.clear(track)
        val empty = document.isInstrumental || document.lines.isEmpty()
        val spl = if (empty) "" else SpwLyricsEncoder.encode(document)
        val raw = document.rawTtml.takeUnless { empty }
        val sourceOffset = document.metadata[EXTERNAL_SOURCE_OFFSET]?.firstOrNull()?.toLongOrNull() ?: 0
        val offset = (offsetMs.toLong() + sourceOffset).coerceIn(-600_000, 600_000)
        return external.submit(track, if (empty) "empty" else if (raw != null) "ttml" else "lrc",
            raw ?: spl, spl, offset)
    }

    private fun matchingExternal(query: TrackQuery): ExternalPlayback? = external.snapshot()?.takeIf {
        it.sessionId == query.playbackSessionId && it.trackRevision == query.playbackTrackRevision && it.uri == query.playbackUri &&
            it.provider == query.playbackProvider && it.sourceName.canonicalName == query.playbackObjectName
    }

    override fun close() {
        if (listening) server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener)
        listening = false
    }

    private companion object {
        val NAME = ObjectName("io.github.gaboron.amllspw:type=TtmlReceiver,version=1")
    }
}
