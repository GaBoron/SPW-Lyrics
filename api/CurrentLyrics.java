/* SPDX-License-Identifier: MIT
 * Copyright (c) 2026 GaBoron
 */
package dev.gaboron.spw.api;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

/** Optional SPW Lyrics client. Requires Java 17; it neither caches lyrics nor loads plugin classes. */
public final class CurrentLyrics {
    private static final MBeanServer SERVER = ManagementFactory.getPlatformMBeanServer();
    private static final ObjectName NAME = name();

    private CurrentLyrics() {}

    public record Identity(String sessionId, long trackRevision, String trackKey) {}

    public record Track(String id, String path, String title, String artist, String album, long durationMs,
        String playbackProvider, String playbackUri, String playbackSessionId, long playbackTrackRevision,
        String playbackObjectName) {}

    /** TTML and SPL have no per-track API offset applied. Apply offsetMs once while rendering. */
    public record Lyrics(String origin, String source, String sourceName, String format, String ttml, String spl,
        long offsetMs, long trackOffsetMs, long sourceOffsetMs, String status, boolean updating) {
        public boolean available() { return status.equals("ready") || status.equals("empty"); }
    }

    public record Snapshot(Identity identity, long revision, Track track, Lyrics lyrics, long updatedAtNanos) {}

    /** Null means no active endpoint/current song or an inconsistent read. Discard any older result. */
    public static Snapshot read() {
        try {
            if (!Integer.valueOf(1).equals(SERVER.getAttribute(NAME, "ProtocolVersion")) ||
                !Boolean.TRUE.equals(SERVER.getAttribute(NAME, "Active"))) return null;
            Object raw = SERVER.getAttribute(NAME, "Snapshot");
            if (!(raw instanceof CompositeData data)) return null;
            Identity identity = new Identity(text(data, "sessionId"), number(data, "trackRevision"), text(data, "trackKey"));
            if (identity.sessionId().isEmpty() || identity.trackRevision() <= 0 || identity.trackKey().isEmpty() ||
                !identity.sessionId().equals(SERVER.getAttribute(NAME, "SessionId")) ||
                !Boolean.TRUE.equals(SERVER.getAttribute(NAME, "Active"))) return null;
            Track track = new Track(text(data, "trackId"), text(data, "path"), text(data, "title"), text(data, "artist"),
                text(data, "album"), number(data, "durationMs"), text(data, "playbackProvider"), text(data, "playbackUri"),
                text(data, "playbackSessionId"), number(data, "playbackTrackRevision"), text(data, "playbackObjectName"));
            Lyrics lyrics = new Lyrics(text(data, "origin"), text(data, "source"), text(data, "sourceName"),
                text(data, "format"), text(data, "ttml"), text(data, "spl"), number(data, "offsetMs"),
                number(data, "trackOffsetMs"), number(data, "sourceOffsetMs"), text(data, "status"),
                Boolean.TRUE.equals(data.get("updating")));
            return new Snapshot(identity, number(data, "revision"), track, lyrics, number(data, "updatedAtNanos"));
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }

    /** True means queued. The endpoint rechecks song identity before performing the action. */
    public static boolean openSearch(Snapshot expected) { return invoke("openSearch", expected); }

    /** Clears this song's manual/local override and restarts automatic matching. */
    public static boolean rematch(Snapshot expected) { return invoke("rematch", expected); }

    private static boolean invoke(String operation, Snapshot expected) {
        if (expected == null || expected.identity() == null) return false;
        Identity id = expected.identity();
        try {
            return Boolean.TRUE.equals(SERVER.invoke(NAME, operation,
                new Object[] { id.sessionId(), id.trackRevision(), id.trackKey() },
                new String[] { "java.lang.String", "long", "java.lang.String" }));
        } catch (Exception | LinkageError unavailable) {
            return false;
        }
    }

    private static String text(CompositeData data, String key) {
        return data.containsKey(key) && data.get(key) instanceof String value ? value : "";
    }

    private static long number(CompositeData data, String key) {
        return data.containsKey(key) && data.get(key) instanceof Number value ? value.longValue() : 0;
    }

    private static ObjectName name() {
        try { return new ObjectName("dev.gaboron.spw:type=Lyrics,version=1"); }
        catch (javax.management.MalformedObjectNameException impossible) { throw new ExceptionInInitializerError(impossible); }
    }
}
