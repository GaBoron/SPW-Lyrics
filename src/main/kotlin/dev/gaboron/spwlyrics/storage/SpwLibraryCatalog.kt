package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/** Reads SPW's Room database without modifying or locking the user's music library. */
internal class SpwLibraryCatalog(private val database: Path) {
    fun load(): List<TrackQuery> {
        if (!Files.isRegularFile(database)) return emptyList()
        Class.forName("org.sqlite.JDBC")
        val location = database.toAbsolutePath().toString().replace('\\', '/')
        return DriverManager.getConnection("jdbc:sqlite:file:$location?mode=ro").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(TRACK_QUERY).use { rows ->
                    buildList {
                        while (rows.next()) {
                            val title = rows.getString("title").orEmpty().trim()
                            val artists = TrackQuery.splitArtists(rows.getString("artist").orEmpty())
                            val album = rows.getString("album").orEmpty().trim()
                            if (title.isBlank() || artists.isEmpty() && album.isBlank()) continue
                            add(
                                TrackQuery(
                                    title = title,
                                    artists = artists,
                                    album = album,
                                    albumArtists = TrackQuery.splitArtists(rows.getString("albumArtist").orEmpty()),
                                    path = rows.getString("path").orEmpty(),
                                    durationMs = rows.getLong("duration").takeIf { !rows.wasNull() && it > 0 },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TRACK_QUERY = """
            SELECT title, artist, album, albumArtist, path, duration
            FROM Track
            WHERE readable != 0
            ORDER BY title COLLATE NOCASE, artist COLLATE NOCASE
        """
    }
}
