package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.WorkshopApi
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
        return PreparedInvocation(loader, method).also { cached = it }.let { invokeReload(it, mediaId) }
    }

    private fun controllerService(): Any? = runCatching {
        val controller = Class.forName(PLAYBACK_CONTROLLER)
        val field = controller.declaredFields.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) && candidate.type.name == PLAYBACK_SERVICE
        } ?: return@runCatching null
        field.trySetAccessible()
        field.get(null)
    }.getOrNull()

    // The host schedules the load and rejects IDs that no longer belong to its current song.
    private fun invokeReload(prepared: PreparedInvocation, mediaId: String): Boolean = runCatching {
        prepared.method.invoke(prepared.loader, mediaId)
    }.isSuccess

    private data class PreparedInvocation(val loader: Any, val method: Method)

    companion object {
        private const val PLAYBACK_SERVICE = "com.xuncorp.voxzen.service.PlaybackService"
        private const val PLAYBACK_CONTROLLER = "com.xuncorp.voxzen.service.PlaybackController"
        private const val LYRICS_LOADER = "com.xuncorp.voxzen.service.PlaybackLyricsLoader"
    }
}
