package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.WorkshopApi
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

internal class SpwPlaybackLyricsReloader(
    private val roots: () -> List<Any> = {
        runCatching { listOf(WorkshopApi.instance, WorkshopApi.playback) }.getOrDefault(emptyList())
    },
) {
    @Volatile private var cached: PreparedInvocation? = null

    fun reload(): Boolean {
        cached?.let { if (invokeCurrent(it)) return true }
        val service = controllerService() ?: ObjectGraphProbe.find(
            roots = roots(),
            maxDepth = 5,
            matches = { it.javaClass.name == PLAYBACK_SERVICE },
            mayTraverse = { type ->
                type.name == PLAYBACK_SERVICE || type.name.startsWith("com.xuncorp.voxzen") ||
                    type.name.startsWith("com.xuncorp.spw")
            },
        ) ?: return false
        val method = service.javaClass.methods.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) && candidate.name == ACCESS_UPDATE_LYRICS &&
                candidate.parameterCount == 3 && candidate.parameterTypes[0].isInstance(service) &&
                candidate.parameterTypes[1].name == MEDIA_ITEM &&
                Continuation::class.java.isAssignableFrom(candidate.parameterTypes[2])
        } ?: return false
        return PreparedInvocation(service, method).also { cached = it }.let(::invokeCurrent)
    }

    private fun controllerService(): Any? = runCatching {
        val controller = Class.forName(PLAYBACK_CONTROLLER)
        val field = controller.declaredFields.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) && candidate.type.name == PLAYBACK_SERVICE
        } ?: return@runCatching null
        field.trySetAccessible()
        field.get(null)
    }.getOrNull()

    private fun invokeCurrent(prepared: PreparedInvocation): Boolean {
        val player = prepared.service.findZeroArgumentResult(PLAYER) ?: return false
        val mediaItem = player.findZeroArgumentResult(MEDIA_ITEM) ?: return false
        return runCatching { prepared.method.invoke(null, prepared.service, mediaItem, Completion) }.isSuccess
    }

    private fun Any.findZeroArgumentResult(returnTypeName: String): Any? = allMethods(javaClass)
        .firstOrNull { it.parameterCount == 0 && it.returnType.name == returnTypeName }
        ?.let { method ->
            runCatching {
                method.trySetAccessible()
                method.invoke(this)
            }.getOrNull()
        }

    private fun allMethods(type: Class<*>): Sequence<Method> = generateSequence(type) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }

    private data class PreparedInvocation(val service: Any, val method: Method)

    private object Completion : Continuation<Any?> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Any?>) = Unit
    }

    companion object {
        private const val PLAYBACK_SERVICE = "com.xuncorp.voxzen.service.PlaybackService"
        private const val PLAYBACK_CONTROLLER = "com.xuncorp.voxzen.service.PlaybackController"
        private const val PLAYER = "com.xuncorp.pisces.PiscesPlayer"
        private const val MEDIA_ITEM = "com.xuncorp.pisces.PiscesMediaItem"
        private const val ACCESS_UPDATE_LYRICS = "access\$updateLyrics"
    }
}
