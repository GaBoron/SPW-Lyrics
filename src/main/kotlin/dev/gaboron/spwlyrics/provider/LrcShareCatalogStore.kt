package dev.gaboron.spwlyrics.provider

import java.awt.Desktop
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

/** Persists the catalog and its session together; an expired snapshot cannot reject searches. */
internal class LrcShareCatalogStore(private val directory: Path) {
    private val path = directory.resolve("catalog.json")

    fun load(identity: String, now: Long): LrcShareCatalog? = runCatching {
        providerJson.decodeFromString<LrcShareCatalog>(Files.readString(path))
            .takeIf { it.isCurrent(identity, now) }
    }.getOrNull()

    fun save(catalog: LrcShareCatalog) {
        // A read-only cache must not discard a successfully fetched in-memory catalog.
        runCatching {
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, "catalog-", ".tmp")
            try {
                Files.writeString(temporary, providerJson.encodeToString(catalog))
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (Files.exists(temporary)) {
                    val recycled = runCatching {
                        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                            Desktop.getDesktop().moveToTrash(temporary.toFile())
                    }.getOrDefault(false)
                    if (!recycled) System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING,
                        "LrcShare 临时目录快照保留在：$temporary")
                }
            }
        }.onFailure {
            System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "LrcShare 目录快照未能写入缓存。")
        }
    }
}

@Serializable
internal data class LrcShareCatalog(
    val keyFingerprint: String,
    val fetchedAt: Long,
    val sessionToken: String,
    val text: String,
) {
    fun isCurrent(identity: String, now: Long): Boolean = sessionToken.isNotBlank() &&
        keyFingerprint == identity && now >= fetchedAt && now - fetchedAt < TTL_MILLIS

    fun contains(value: String): Boolean = value.trim().lowercase() in text

    companion object {
        private val TTL_MILLIS = Duration.ofHours(24).toMillis()

        fun fromResponse(data: JsonObject, identity: String, fetchedAt: Long): LrcShareCatalog = LrcShareCatalog(
            keyFingerprint = identity,
            fetchedAt = fetchedAt,
            sessionToken = checkNotNull(data.string("session_token")?.takeIf(String::isNotBlank)) { "LrcShare 会话获取失败" },
            text = checkNotNull(data.string("text")) { "LrcShare 目录快照缺失" }.lowercase(),
        )
    }
}
