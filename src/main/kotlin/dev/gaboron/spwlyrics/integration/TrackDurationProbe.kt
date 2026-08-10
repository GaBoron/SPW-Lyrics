package dev.gaboron.spwlyrics.integration

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

fun interface TrackDurationProbe {
    fun durationMs(path: String): Long?
}

class CachedTrackDurationProbe(
    private val reader: (Path) -> Long? = ReflectiveJAudioTaggerDurationReader::read,
) : TrackDurationProbe {
    private val durations = ConcurrentHashMap<String, Long>()

    override fun durationMs(path: String): Long? {
        if (path.isBlank()) return null
        durations[path]?.let { return it.takeIf { duration -> duration > 0 } }
        val duration = runCatching { Path.of(path) }.getOrNull()
            ?.takeIf(Files::isRegularFile)
            ?.let { runCatching { reader(it) }.getOrNull() }
            ?.takeIf { it > 0 }
        durations[path] = duration ?: UNAVAILABLE
        return duration
    }

    private companion object {
        const val UNAVAILABLE = -1L
    }
}

private object ReflectiveJAudioTaggerDurationReader {
    fun read(path: Path): Long? {
        val audioFileIo = loadClass("org.jaudiotagger.audio.AudioFileIO") ?: return null
        val audioFile = audioFileIo.getMethod("read", File::class.java).invoke(null, path.toFile()) ?: return null
        val header = audioFile.javaClass.getMethod("getAudioHeader").invoke(audioFile) ?: return null
        val seconds = (header.javaClass.getMethod("getPreciseTrackLength").invoke(header) as? Number)?.toDouble()
            ?: return null
        return (seconds * 1_000).roundToLong().takeIf { it > 0 }
    }

    private fun loadClass(name: String): Class<*>? = sequenceOf(
        Thread.currentThread().contextClassLoader,
        ReflectiveJAudioTaggerDurationReader::class.java.classLoader,
        ClassLoader.getSystemClassLoader(),
    ).filterNotNull().distinct().firstNotNullOfOrNull { loader ->
        runCatching { Class.forName(name, true, loader) }.getOrNull()
    }
}
