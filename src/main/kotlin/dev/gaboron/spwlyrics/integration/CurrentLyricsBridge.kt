package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.CurrentLyricsSelection
import dev.gaboron.spwlyrics.codec.SpwLyricsEncoder
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.Executors
import javax.management.MBeanServer
import javax.management.ObjectName
import javax.management.StandardMBean
import javax.management.openmbean.CompositeData
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.CompositeType
import javax.management.openmbean.OpenType
import javax.management.openmbean.SimpleType

internal interface CurrentLyricsMBean {
    fun getProtocolVersion(): Int
    fun getActive(): Boolean
    fun getSessionId(): String
    fun getSnapshot(): CompositeData?
    fun openSearch(sessionId: String, trackRevision: Long, trackKey: String): Boolean
    fun rematch(sessionId: String, trackRevision: Long, trackKey: String): Boolean
}

/** Process-local read/action endpoint. It never owns playback, provider requests or UI state. */
internal class CurrentLyricsBridge(
    private val current: () -> TrackQuery?,
    private val selection: () -> CurrentLyricsSelection?,
    private val original: (TrackQuery, Boolean) -> LyricsDocument?,
    private val canOpenSearch: () -> Boolean,
    private val open: (TrackQuery, () -> Boolean) -> Unit,
    private val match: (TrackQuery, () -> Boolean) -> Unit,
    private val server: MBeanServer = ManagementFactory.getPlatformMBeanServer(),
) : CurrentLyricsMBean, AutoCloseable {
    private data class View(val query: TrackQuery, val selected: CurrentLyricsSelection?, val document: LyricsDocument?)
    private data class Identity(val query: TrackQuery, val generation: Long)
    private val lock = Any()
    private val reads = Any()
    private val sessionId = UUID.randomUUID().toString()
    private val actions = Executors.newSingleThreadExecutor { Thread(it, "spw-lyrics-public-actions").apply { isDaemon = true } }
    @Volatile private var closed = false
    private var registered = false
    private var identity: Identity? = null
    private var view: View? = null
    private var snapshot: CompositeData? = null
    private var revision = 0L
    private var trackRevision = 0L

    fun start() {
        synchronized(lock) {
            check(!closed && !registered)
            server.registerMBean(StandardMBean(this, CurrentLyricsMBean::class.java), NAME)
            registered = true
        }
    }

    override fun getProtocolVersion() = 1
    override fun getActive() = !closed
    override fun getSessionId() = sessionId

    override fun getSnapshot(): CompositeData? = synchronized(reads) {
        val observed = readView()
        synchronized(lock) state@ {
            if (closed || observed == null) {
                if (view != null) revision++
                view = null
                identity = null
                snapshot = null
                return@state null
            }
            val nextIdentity = Identity(observed.query, observed.selected?.trackGeneration ?: -1)
            if (identity != nextIdentity) {
                identity = nextIdentity
                trackRevision++
            }
            if (view != observed || snapshot == null) {
                view = observed
                revision++
                snapshot = encode(observed)
            }
            snapshot
        }
    }

    /** Never starts a provider request; recheck ownership after reading the slot or external lyrics. */
    private fun readView(): View? = runCatching {
        repeat(2) {
            if (closed) return null
            val query = current() ?: return null
            val selected = selection()?.takeIf { sameTrack(it.query, query) }
            // External source lyrics may be revoked without changing the track identity. Read their
            // current revision even when the load coordinator still holds a previously converted copy.
            val externalOriginal = query.isExternalPlayback && selected?.document?.source == LyricsSource.LOCAL
            val document = if (selected?.followingHost == true || externalOriginal) {
                original(query, selected.embedded)
            } else selected?.document
            if (sameTrack(query, current()) && selection()?.takeIf { sameTrack(it.query, query) } == selected && !closed) {
                return View(query, selected, document)
            }
        }
        null
    }.getOrNull()

    private fun encode(value: View): CompositeData {
        val query = value.query
        val selected = value.selected
        val document = value.document
        val empty = document != null && (document.lines.isEmpty() || document.isInstrumental)
        val raw = document?.rawTtml.orEmpty().takeUnless { empty }.orEmpty()
        val spl = if (document == null || empty) "" else SpwLyricsEncoder.encode(document)
        val trackOffset = selected?.delayMs?.toLong() ?: 0L
        val sourceOffset = (document?.metadata?.get(EXTERNAL_SOURCE_OFFSET)?.firstOrNull()?.toLongOrNull() ?: 0L)
            .coerceIn(-600_000, 600_000)
        val follows = selected?.followingHost == true
        val local = follows || document?.source == LyricsSource.LOCAL
        val fields = linkedMapOf<String, Any>(
            "sessionId" to sessionId, "revision" to revision, "trackRevision" to trackRevision,
            "trackKey" to query.key, "trackId" to query.trackId, "path" to query.path,
            "title" to query.title, "artist" to query.artists.joinToString(" / "), "album" to query.album,
            "durationMs" to (query.durationMs ?: 0L),
            "playbackProvider" to query.playbackProvider, "playbackUri" to query.playbackUri,
            "playbackSessionId" to query.playbackSessionId, "playbackTrackRevision" to query.playbackTrackRevision,
            "playbackObjectName" to query.playbackObjectName,
            "origin" to if (local && query.isExternalPlayback) "external" else if (local) "host" else "plugin",
            "source" to (document?.source?.name ?: ""),
            "sourceName" to (if (local && query.isExternalPlayback && document != null) query.playbackProvider else document?.source?.displayName.orEmpty()),
            "format" to when {
                document == null -> ""
                empty -> "empty"
                raw.isNotEmpty() -> "ttml"
                document.quality == LyricsQuality.PLAIN -> "plain"
                else -> "lrc"
            },
            "ttml" to raw, "spl" to spl,
            "offsetMs" to (trackOffset + sourceOffset).coerceIn(-600_000, 600_000),
            "trackOffsetMs" to trackOffset, "sourceOffsetMs" to sourceOffset,
            "status" to when {
                document != null -> if (empty) "empty" else "ready"
                selected?.updating == true -> "loading"
                follows -> "following_host"
                else -> "unavailable"
            },
            "updating" to (selected?.updating == true), "updatedAtNanos" to System.nanoTime(),
        )
        val keys = fields.keys.toTypedArray()
        val types = fields.values.map { field -> when (field) {
            is String -> SimpleType.STRING
            is Boolean -> SimpleType.BOOLEAN
            else -> SimpleType.LONG
        } as OpenType<*> }.toTypedArray()
        return CompositeDataSupport(CompositeType("CurrentLyricsSnapshot", "CurrentLyricsSnapshot", keys, keys, types), fields)
    }

    override fun openSearch(sessionId: String, trackRevision: Long, trackKey: String): Boolean =
        dispatch(sessionId, trackRevision, trackKey, search = true)

    override fun rematch(sessionId: String, trackRevision: Long, trackKey: String): Boolean =
        dispatch(sessionId, trackRevision, trackKey, search = false)

    private fun dispatch(expectedSession: String, expectedTrack: Long, expectedKey: String, search: Boolean): Boolean {
        if (search && !runCatching(canOpenSearch).getOrDefault(false)) return false
        val expected = getSnapshot()?.takeIf {
            it["sessionId"] == expectedSession && it["trackRevision"] == expectedTrack && it["trackKey"] == expectedKey
        } ?: return false
        val query = synchronized(lock) { view?.query } ?: return false
        val stillCurrent = {
            getSnapshot()?.let { it["sessionId"] == expected["sessionId"] &&
                it["trackRevision"] == expected["trackRevision"] && it["trackKey"] == expected["trackKey"] } == true
        }
        return runCatching {
            actions.execute {
                if (stillCurrent()) runCatching { if (search) open(query, stillCurrent) else match(query, stillCurrent) }
            }
            true
        }.getOrDefault(false)
    }

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                view = null
                identity = null
                snapshot = null
                actions.shutdownNow()
            }
            if (registered && runCatching { server.getAttribute(NAME, "SessionId") == sessionId }.getOrDefault(false)) {
                server.unregisterMBean(NAME)
            }
            registered = false
        }
    }

    private fun sameTrack(left: TrackQuery, right: TrackQuery?): Boolean = right != null &&
        left.key == right.key && left.trackId == right.trackId && left.playbackProvider == right.playbackProvider &&
        left.playbackSessionId == right.playbackSessionId && left.playbackTrackRevision == right.playbackTrackRevision &&
        left.playbackObjectName == right.playbackObjectName

    companion object {
        val NAME = ObjectName("dev.gaboron.spw:type=Lyrics,version=1")
    }
}
