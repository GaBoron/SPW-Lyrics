/* SPDX-License-Identifier: MIT
 * Copyright (c) 2026 GaBoron
 */
package dev.gaboron.spw.api;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

/** A process-local external playback publisher. Requires Java 17 and no plugin dependencies. */
public final class ExternalPlayback implements AutoCloseable {
    public static final long LEASE_NANOS = 15_000_000_000L;
    private static final long MAX_TIME_MS = 31_536_000_000L;
    private static final Set<String> COMMANDS = Set.of("play", "pause", "previous", "next", "seek", "volume");
    private static final CompositeType SNAPSHOT_TYPE = type("ExternalPlaybackSnapshot",
        "provider", SimpleType.STRING, "sessionId", SimpleType.STRING,
        "revision", SimpleType.LONG, "trackRevision", SimpleType.LONG,
        "active", SimpleType.BOOLEAN, "playing", SimpleType.BOOLEAN,
        "uri", SimpleType.STRING, "title", SimpleType.STRING,
        "artist", SimpleType.STRING, "album", SimpleType.STRING,
        "coverUrl", SimpleType.STRING, "durationMs", SimpleType.LONG,
        "positionMs", SimpleType.LONG, "sampledAtNanos", SimpleType.LONG,
        "volumePercent", SimpleType.INTEGER, "playbackSpeed", SimpleType.DOUBLE,
        "activatedAtNanos", SimpleType.LONG, "updatedAtNanos", SimpleType.LONG);
    private static final CompositeType LYRICS_TYPE = type("ExternalPlaybackLyrics",
        "sessionId", SimpleType.STRING, "trackRevision", SimpleType.LONG, "uri", SimpleType.STRING,
        "format", SimpleType.STRING, "content", SimpleType.STRING, "spl", SimpleType.STRING,
        "offsetMs", SimpleType.LONG, "revision", SimpleType.LONG);

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final String provider;
    private final String sessionId = UUID.randomUUID().toString();
    private final ObjectName name;
    private final Controls controls;
    private Track track;
    private boolean active;
    private boolean playing;
    private boolean closed;
    private long revision;
    private long trackRevision;
    private long positionMs;
    private long sampledAtNanos = System.nanoTime();
    private long activatedAtNanos;
    private long updatedAtNanos = sampledAtNanos;
    private int volumePercent = 100;
    private double playbackSpeed = 1.0;
    private Lyrics sourceLyrics;
    private Lyrics selectedLyrics;
    private long sourceLyricsRevision;
    private long lyricsRevision;

    /** A stable item identifier belongs in uri. An authenticated playback URL belongs in your player. */
    public record Track(String uri, String title, String artist, String album, String coverUrl, long durationMs) {
        public Track {
            text(uri, "uri", 2_048);
            URI parsed = URI.create(uri);
            require(parsed.isAbsolute() && parsed.getRawUserInfo() == null && uri.indexOf('?') < 0 &&
                parsed.getRawFragment() == null, "uri must be an absolute identifier without credentials, query or fragment");
            text(title, "title", 4_096);
            text(artist, "artist", 4_096);
            text(album, "album", 4_096);
            text(coverUrl, "coverUrl", 4_096);
            if (!coverUrl.isEmpty()) {
                URI cover = URI.create(coverUrl);
                require(Set.of("http", "https").contains(cover.getScheme()) && cover.getHost() != null &&
                    cover.getRawUserInfo() == null, "coverUrl must be an HTTP(S) URL without user information");
            }
            require(durationMs >= 0 && durationMs <= MAX_TIME_MS, "durationMs must be between 0 and one year");
        }
    }

    /** Null lyrics mean unavailable. Format empty is an explicit empty/instrumental selection. */
    public record Lyrics(String format, String content, String spl, long offsetMs) {
        public Lyrics {
            require(Set.of("ttml", "lrc", "empty").contains(format), "unsupported lyric format");
            text(content, "content", 2_000_000);
            text(spl, "spl", 2_000_000);
            require(offsetMs >= -600_000 && offsetMs <= 600_000, "offsetMs must be within ten minutes");
            require(!format.equals("empty") || content.isEmpty() && spl.isEmpty(), "empty lyrics must have empty content and spl");
        }

        public static Lyrics lrc(String content, long offsetMs) {
            return new Lyrics("lrc", content, content, offsetMs);
        }
    }

    /** Retain this token when scheduling asynchronous commands or lyric work. */
    public record Identity(String endpoint, String provider, String sessionId, long trackRevision, String uri) {}

    /** Return promptly after validating or enqueueing. Unsupported commands return false. */
    @FunctionalInterface
    public interface Controls {
        boolean accept(Identity expected, String command, long value);
    }

    /** The wire interface deliberately contains only types supplied by the JDK. */
    public interface ExchangeMBean {
        int getProtocolVersion();
        CompositeData getSnapshot();
        CompositeData getSourceLyrics();
        CompositeData getLyrics();
        boolean control(String sessionId, long trackRevision, String uri, String command, long value);
        boolean submitLyrics(String sessionId, long trackRevision, String uri,
            String format, String content, String spl, long offsetMs);
        boolean clearLyrics(String sessionId, long trackRevision, String uri);
    }

    public ExternalPlayback(String provider) {
        this(provider, null);
    }

    public ExternalPlayback(String provider, Controls controls) {
        require(provider != null && provider.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"), "invalid provider ID");
        this.provider = provider;
        this.controls = controls;
        try {
            name = new ObjectName("dev.gaboron.spw:type=ExternalPlayback,version=1,provider=" + ObjectName.quote(provider));
            synchronized (server) {
                server.registerMBean(new StandardMBean(new Exchange(), ExchangeMBean.class, false), name);
            }
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot register external playback provider " + provider, failure);
        }
    }

    /** Publish the selected local source. Pausing keeps active true and sets playing false. */
    public synchronized void publish(Track next, boolean active, boolean playing,
        long positionMs, int volumePercent, double playbackSpeed) {
        checkOpen();
        Objects.requireNonNull(next, "track");
        require(positionMs >= 0 && positionMs <= MAX_TIME_MS, "positionMs must be between 0 and one year");
        require(volumePercent >= 0 && volumePercent <= 100, "volumePercent must be between 0 and 100");
        require(Double.isFinite(playbackSpeed) && playbackSpeed >= 0.1 && playbackSpeed <= 8.0,
            "playbackSpeed must be between 0.1 and 8");
        long now = System.nanoTime();
        boolean reacquiring = active && (!this.active || !leaseValid(now));
        if (track == null || !track.uri().equals(next.uri()) || this.active != active || reacquiring) advanceTrack();
        if (reacquiring) activatedAtNanos = now;
        track = next;
        this.active = active;
        this.playing = active && playing;
        this.positionMs = next.durationMs() > 0 ? Math.min(positionMs, next.durationMs()) : positionMs;
        this.volumePercent = volumePercent;
        this.playbackSpeed = playbackSpeed;
        sampledAtNanos = now;
        updatedAtNanos = now;
        revision++;
    }

    /** Refresh liveness without changing the position sample, including while paused. */
    public synchronized void touch() {
        checkOpen();
        long now = System.nanoTime();
        if (active && !leaseValid(now)) {
            advanceTrack();
            activatedAtNanos = now;
        }
        updatedAtNanos = now;
        revision++;
    }

    public synchronized Identity identity() {
        return track == null ? null : new Identity(name.getCanonicalName(), provider, sessionId, trackRevision, track.uri());
    }

    /** Recheck immediately before an asynchronous backend acts on a captured identity. */
    public synchronized boolean isCurrent(Identity expected) {
        return expected != null && usable() && expected.equals(identity());
    }

    public synchronized void setSourceLyrics(Lyrics lyrics) {
        checkOpen();
        require(track != null, "publish a track before its lyrics");
        sourceLyrics = lyrics;
        sourceLyricsRevision++;
        if (selectedLyrics == null) lyricsRevision++;
    }

    /** Publish asynchronously fetched source lyrics only while their item is still selected. */
    public synchronized boolean publishLyrics(Identity expected, Lyrics lyrics) {
        if (!isCurrent(expected)) return false;
        setSourceLyrics(lyrics);
        return true;
    }

    private synchronized CompositeData snapshot() {
        return data(SNAPSHOT_TYPE, Map.ofEntries(
            Map.entry("provider", provider), Map.entry("sessionId", sessionId),
            Map.entry("revision", revision), Map.entry("trackRevision", trackRevision),
            Map.entry("active", usable()), Map.entry("playing", usable() && playing),
            Map.entry("uri", track == null ? "" : track.uri()),
            Map.entry("title", track == null ? "" : track.title()),
            Map.entry("artist", track == null ? "" : track.artist()),
            Map.entry("album", track == null ? "" : track.album()),
            Map.entry("coverUrl", track == null ? "" : track.coverUrl()),
            Map.entry("durationMs", track == null ? 0L : track.durationMs()),
            Map.entry("positionMs", positionMs), Map.entry("sampledAtNanos", sampledAtNanos),
            Map.entry("volumePercent", volumePercent), Map.entry("playbackSpeed", playbackSpeed),
            Map.entry("activatedAtNanos", activatedAtNanos), Map.entry("updatedAtNanos", updatedAtNanos)));
    }

    private CompositeData lyrics(Lyrics value, long revision) {
        if (value == null || track == null) return null;
        return data(LYRICS_TYPE, Map.of("sessionId", sessionId, "trackRevision", trackRevision,
            "uri", track.uri(), "format", value.format(), "content", value.content(), "spl", value.spl(),
            "offsetMs", value.offsetMs(), "revision", revision));
    }

    private boolean usable() {
        return !closed && active && track != null && leaseValid(System.nanoTime());
    }

    private boolean leaseValid(long now) {
        long age = now - updatedAtNanos;
        return age >= 0 && age <= LEASE_NANOS;
    }

    private void advanceTrack() {
        trackRevision++;
        sourceLyrics = null;
        selectedLyrics = null;
        sourceLyricsRevision++;
        lyricsRevision++;
    }

    private boolean matches(String expectedSession, long expectedRevision, String uri) {
        return usable() && sessionId.equals(expectedSession) && trackRevision == expectedRevision && track.uri().equals(uri);
    }

    private synchronized boolean control(String session, long revision, String uri, String command, long value) {
        if (!matches(session, revision, uri) || controls == null || command == null || !COMMANDS.contains(command)) return false;
        if (command.equals("seek")) value = Math.max(0, Math.min(value, track.durationMs() > 0 ? track.durationMs() : MAX_TIME_MS));
        if (command.equals("volume")) value = Math.max(0, Math.min(value, 100));
        try {
            return controls.accept(identity(), command, value);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private synchronized boolean submitLyrics(String session, long revision, String uri,
        String format, String content, String spl, long offsetMs) {
        if (!matches(session, revision, uri)) return false;
        try {
            selectedLyrics = new Lyrics(format, content, spl, offsetMs);
            lyricsRevision++;
            return true;
        } catch (IllegalArgumentException | NullPointerException failure) {
            return false;
        }
    }

    private synchronized boolean clearLyrics(String session, long revision, String uri) {
        if (!matches(session, revision, uri)) return false;
        if (selectedLyrics != null) {
            selectedLyrics = null;
            lyricsRevision++;
        }
        return true;
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            active = false;
            playing = false;
            sourceLyrics = null;
            selectedLyrics = null;
        }
        try {
            synchronized (server) {
                CompositeData current = (CompositeData) server.getAttribute(name, "Snapshot");
                if (sessionId.equals(current.get("sessionId"))) server.unregisterMBean(name);
            }
        } catch (InstanceNotFoundException alreadyRemoved) {
            // Another lifecycle owner has already unregistered this endpoint.
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot unregister external playback provider " + provider, failure);
        }
    }

    private final class Exchange implements ExchangeMBean {
        @Override public int getProtocolVersion() { return 1; }
        @Override public CompositeData getSnapshot() { return snapshot(); }
        @Override public CompositeData getSourceLyrics() {
            synchronized (ExternalPlayback.this) { return lyrics(sourceLyrics, sourceLyricsRevision); }
        }
        @Override public CompositeData getLyrics() {
            synchronized (ExternalPlayback.this) {
                return lyrics(selectedLyrics == null ? sourceLyrics : selectedLyrics, lyricsRevision);
            }
        }
        @Override public boolean control(String session, long revision, String uri, String command, long value) {
            return ExternalPlayback.this.control(session, revision, uri, command, value);
        }
        @Override public boolean submitLyrics(String session, long revision, String uri,
            String format, String content, String spl, long offsetMs) {
            return ExternalPlayback.this.submitLyrics(session, revision, uri, format, content, spl, offsetMs);
        }
        @Override public boolean clearLyrics(String session, long revision, String uri) {
            return ExternalPlayback.this.clearLyrics(session, revision, uri);
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("External playback publisher is closed");
    }

    private static void text(String value, String name, int limit) {
        Objects.requireNonNull(value, name);
        require(value.length() <= limit, name + " exceeds its length limit");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private static CompositeData data(CompositeType type, Map<String, ?> values) {
        try {
            return new CompositeDataSupport(type, values);
        } catch (OpenDataException invalidSchema) {
            throw new IllegalStateException("Invalid external playback schema", invalidSchema);
        }
    }

    private static CompositeType type(String name, Object... fields) {
        String[] names = new String[fields.length / 2];
        OpenType<?>[] types = new OpenType<?>[names.length];
        for (int index = 0; index < names.length; index++) {
            names[index] = (String) fields[index * 2];
            types[index] = (OpenType<?>) fields[index * 2 + 1];
        }
        try {
            return new CompositeType(name, name, names, names, types);
        } catch (OpenDataException invalidSchema) {
            throw new ExceptionInInitializerError(invalidSchema);
        }
    }
}
