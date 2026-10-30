package dev.gaboron.spwlyrics.integration.hostui

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

/** Descriptors only: bridge objects, host instances, class loaders and Hook handles are never cached. */
internal data class HostUiTargets(
    val main: HostUiDiscovery.Targets,
    val popup: HostPopupMaterialDiscovery.Targets?,
    val menu: HostPlayerMenuDiscovery.Targets?,
) {
    val complete: Boolean get() = popup != null && menu != null
}

internal data class HostUiSource(val path: Path, val hostClass: String, val size: Long, val modified: String) {
    companion object {
        fun read(path: Path, hostClass: String): HostUiSource? {
            val resolved = path.toRealPath()
            val attributes = Files.readAttributes(resolved, BasicFileAttributes::class.java)
            // A directory timestamp does not reflect changes to its nested class files.
            if (!attributes.isRegularFile) return null
            return HostUiSource(resolved, hostClass, attributes.size(), attributes.lastModifiedTime().toString())
        }
    }
}

/** A small, disposable target cache. Source changes and malformed entries are ordinary cache misses. */
internal class HostUiTargetCache(private val path: Path) {
    fun read(source: HostUiSource): HostUiTargets? = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) return null
        val values = Properties().apply { Files.newBufferedReader(path).use(::load) }
        if (values.getProperty("format") != FORMAT || values.getProperty("valid") != "true" ||
            values.getProperty("source.path") != source.path.toString() ||
            values.getProperty("source.class") != source.hostClass ||
            values.getProperty("source.size") != source.size.toString() ||
            values.getProperty("source.modified") != source.modified) return null
        HostUiTargets(
            main = HostUiDiscovery.Targets(
                values.site("main.page"), values.site("main.captionEntry"), values.method("main.captionControl"),
                values.method("main.back"), values.method("main.closeWindow"), values.method("main.navigate"),
                values.method("main.collapsePlayer"), values.field("main.playerState"), values.method("main.playerExpanded"),
                values.items("main.navigation") { values.method(it) }, values.field("main.backStack"),
                values.items("main.locals") { values.field(it) },
                HostThemeDiscovery.Targets(values.method("theme.configs"), values.method("theme.dark")),
            ),
            popup = HostPopupMaterialDiscovery.Targets(values.method("popup.modifier"), values.field("popup.material")),
            menu = HostPlayerMenuDiscovery.Targets(
                values.method("menu.builder"), values.required("menu.scope"),
                values.required("menu.scopeParameter").toInt(), values.required("menu.popupParameter").toInt(),
                values.method("menu.dismiss"), values.method("menu.label"),
            ),
        )
    }.getOrNull()

    /** Publish only complete, successfully installed plans, so partial discovery is retried next enable. */
    fun write(source: HostUiSource, targets: HostUiTargets) {
        if (!targets.complete) return
        val values = Properties().apply {
            setProperty("format", FORMAT)
            setProperty("valid", "true")
            setProperty("source.path", source.path.toString())
            setProperty("source.class", source.hostClass)
            setProperty("source.size", source.size.toString())
            setProperty("source.modified", source.modified)
            val main = targets.main
            putSite("main.page", main.page())
            putSite("main.captionEntry", main.captionEntry())
            putMethod("main.captionControl", main.captionControl())
            putMethod("main.back", main.back())
            putMethod("main.closeWindow", main.closeWindow())
            putMethod("main.navigate", main.navigate())
            putMethod("main.collapsePlayer", main.collapsePlayer())
            putField("main.playerState", main.playerState())
            putMethod("main.playerExpanded", main.playerExpanded())
            putItems("main.navigation", main.navigation()) { key, value -> putMethod(key, value) }
            putField("main.backStack", main.backStack())
            putItems("main.locals", main.locals()) { key, value -> putField(key, value) }
            putMethod("theme.configs", main.theme().configs())
            putMethod("theme.dark", main.theme().dark())
            val popup = checkNotNull(targets.popup)
            putMethod("popup.modifier", popup.modifier())
            putField("popup.material", popup.material())
            val menu = checkNotNull(targets.menu)
            putMethod("menu.builder", menu.builder())
            setProperty("menu.scope", menu.scope())
            setProperty("menu.scopeParameter", menu.scopeParameter().toString())
            setProperty("menu.popupParameter", menu.popupParameter().toString())
            putMethod("menu.dismiss", menu.dismiss())
            putMethod("menu.label", menu.label())
        }
        save(values)
    }

    fun invalidate() {
        // Overwrite with a tombstone: a failed plan must not survive a subsequent failed scan.
        save(Properties().apply {
            setProperty("format", FORMAT)
            setProperty("valid", "false")
        })
    }

    private fun save(values: Properties) {
        runCatching {
            Files.createDirectories(path.parent)
            val pending = Files.createTempFile(path.parent, path.fileName.toString(), ".pending")
            Files.newBufferedWriter(pending).use { values.store(it, "SPW Lyrics host UI targets") }
            try {
                Files.move(pending, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending, path, REPLACE_EXISTING)
            }
        }.onFailure {
            System.getLogger("SPW Lyrics").log(System.Logger.Level.WARNING, "保存宿主 Hook 缓存失败，下次将重新扫描", it)
        }
    }

    private fun Properties.required(key: String): String = checkNotNull(getProperty(key)).also {
        require(it.isNotBlank() && it.length <= 2048)
    }
    private fun Properties.method(key: String) = HostBytecodeIndex.Method(required("$key.owner"), required("$key.name"), required("$key.descriptor"))
    private fun Properties.field(key: String) = HostBytecodeIndex.Field(required("$key.owner"), required("$key.name"), required("$key.descriptor"))
    private fun Properties.site(key: String) = HostUiDiscovery.Site(method("$key.target"), required("$key.caller"))
    private fun <T> Properties.items(key: String, read: (String) -> T): List<T> {
        val count = required("$key.count").toInt()
        require(count in 1..128)
        return List(count) { read("$key.$it") }
    }

    private fun Properties.putMethod(key: String, value: HostBytecodeIndex.Method) {
        setProperty("$key.owner", value.owner())
        setProperty("$key.name", value.name())
        setProperty("$key.descriptor", value.descriptor())
    }
    private fun Properties.putField(key: String, value: HostBytecodeIndex.Field) {
        setProperty("$key.owner", value.owner())
        setProperty("$key.name", value.name())
        setProperty("$key.descriptor", value.descriptor())
    }
    private fun Properties.putSite(key: String, value: HostUiDiscovery.Site) {
        putMethod("$key.target", value.target())
        setProperty("$key.caller", value.caller())
    }
    private fun <T> Properties.putItems(key: String, items: List<T>, write: (String, T) -> Unit) {
        setProperty("$key.count", items.size.toString())
        items.forEachIndexed { index, value -> write("$key.$index", value) }
    }

    private companion object {
        const val FORMAT = "1"
        const val MAX_BYTES = 128 * 1024
    }
}
