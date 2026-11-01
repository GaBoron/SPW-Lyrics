package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.WorkshopApi
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class SpwPlaybackLyricsReloader(
    private val roots: () -> List<Any> = {
        runCatching { listOf(WorkshopApi.instance, WorkshopApi.playback) }.getOrDefault(emptyList())
    },
    private val currentMediaId: () -> String? = { WorkshopApi.playback.getCurrentMediaItem()?.id },
) {
    @Volatile private var cached: PreparedInvocation? = null

    fun reload(): Boolean {
        val mediaId = runCatching(currentMediaId).getOrNull()?.takeIf(String::isNotBlank) ?: return false
        cached?.let {
            if (invokeReload(it, mediaId)) return true
            cached = null
        }
        val service = controllerService() ?: ObjectGraphProbe.find(
            roots = roots(),
            maxDepth = 5,
            matches = { it.javaClass.name == PLAYBACK_SERVICE },
            mayTraverse = { type ->
                type.name == PLAYBACK_SERVICE || type.name.startsWith("com.xuncorp.voxzen") ||
                    type.name.startsWith("com.xuncorp.spw")
            },
        ) ?: return false
        val loader = runCatching {
            val field = generateSequence(service.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { !Modifier.isStatic(it.modifiers) && it.type.name == LYRICS_LOADER }
                ?: return@runCatching null
            field.trySetAccessible()
            field.get(service)
        }.getOrNull() ?: return false
        val method = loader.javaClass.methods.singleOrNull { candidate ->
            !Modifier.isStatic(candidate.modifiers) && candidate.name == "reload" &&
                candidate.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                candidate.returnType == Void.TYPE
        } ?: return false
        val prepared = runCatching {
            val lock = loader.javaClass.getDeclaredField("lock").let { field ->
                check(field.trySetAccessible())
                checkNotNull(field.get(loader))
            }
            val generation = loader.javaClass.getDeclaredField("generation").also { field ->
                check(field.type == Long::class.javaPrimitiveType && field.trySetAccessible())
            }
            PreparedInvocation(loader, method, lock, generation)
        }.getOrNull() ?: return false
        cached = prepared
        return invokeReload(prepared, mediaId)
    }

    private fun controllerService(): Any? = runCatching {
        val controller = Class.forName(PLAYBACK_CONTROLLER)
        val field = controller.declaredFields.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) && candidate.type.name == PLAYBACK_SERVICE
        } ?: return@runCatching null
        field.trySetAccessible()
        field.get(null)
    }.getOrNull()

    // The host silently ignores reloads before its first onLoaded or after release. Observe its
    // generation under the same lock so a no-op cannot be reported as a successful refresh.
    private fun invokeReload(prepared: PreparedInvocation, mediaId: String): Boolean = runCatching {
        synchronized(prepared.lock) {
            val before = prepared.generation.getLong(prepared.loader)
            prepared.method.invoke(prepared.loader, mediaId)
            prepared.generation.getLong(prepared.loader) != before
        }
    }.getOrDefault(false)

    private data class PreparedInvocation(val loader: Any, val method: Method, val lock: Any, val generation: Field)

    companion object {
        private const val PLAYBACK_SERVICE = "com.xuncorp.voxzen.service.PlaybackService"
        private const val PLAYBACK_CONTROLLER = "com.xuncorp.voxzen.service.PlaybackController"
        private const val LYRICS_LOADER = "com.xuncorp.voxzen.service.PlaybackLyricsLoader"
    }
}
