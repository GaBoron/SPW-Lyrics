package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.WorkshopApi
import dev.gaboron.spwlyrics.application.LyricsRefreshBridge
import java.lang.reflect.Modifier

class ReflectiveLyricsRefreshBridge : LyricsRefreshBridge {
    private val playbackReloader = SpwPlaybackLyricsReloader()

    override fun reloadCurrentLyrics(): Boolean {
        if (playbackReloader.reload()) return true
        val apiObjects = runCatching { listOf(WorkshopApi.instance, WorkshopApi.playback) }.getOrDefault(emptyList())
        apiObjects.forEach { root ->
            if (invokeCandidate(root)) return true
            root.javaClass.declaredFields.forEach { field ->
                val nested = runCatching {
                    field.trySetAccessible()
                    field.get(root)
                }.getOrNull() ?: return@forEach
                if (invokeCandidate(nested)) return true
            }
        }
        for (className in TARGET_CLASSES) {
            val targetClass = runCatching { Class.forName(className) }.getOrNull() ?: continue
            val method = targetClass.methods.firstOrNull { candidate ->
                candidate.parameterCount == 0 && candidate.name.lowercase() in METHOD_NAMES
            } ?: continue
            val target = if (Modifier.isStatic(method.modifiers)) null else singleton(targetClass) ?: continue
            if (runCatching { method.invoke(target) }.isSuccess) return true
        }
        return false
    }

    private fun invokeCandidate(target: Any): Boolean {
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.parameterCount == 0 && candidate.name.lowercase() in METHOD_NAMES
        } ?: return false
        return runCatching { method.invoke(target) }.isSuccess
    }

    private fun singleton(type: Class<*>): Any? {
        val field = type.declaredFields.firstOrNull { it.name in setOf("INSTANCE", "instance") } ?: return null
        return runCatching {
            field.trySetAccessible()
            field.get(null)
        }.getOrNull()
    }

    companion object {
        private val METHOD_NAMES = setOf("reloadlyrics", "loadlyrics", "updatelyrics", "refreshlyrics")
        private val TARGET_CLASSES = listOf(
            "com.xuncorp.voxzen.service.PlaybackService",
            "com.xuncorp.spw.service.PlaybackService",
            "com.xuncorp.spw.playback.PlaybackService",
        )
    }
}
