package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.AudioArtwork
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** Bound input and decoded dimensions before accepting a local or downloaded cover. */
internal object AudioArtworkImages {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_PIXELS = 25_000_000L

    fun read(path: Path): AudioArtwork = Files.newInputStream(path).use { decode(readBounded(it)) }

    fun readBounded(stream: InputStream): ByteArray {
        val bytes = stream.readNBytes(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "封面过大，请选择不超过 20 MiB 的图片。" }
        return bytes
    }

    fun decode(bytes: ByteArray): AudioArtwork = image(bytes) { reader ->
        val mime = when (reader.formatName.lowercase()) {
            "jpeg", "jpg" -> "image/jpeg"
            "png" -> "image/png"
            else -> error("封面只支持 JPEG 或 PNG 图片。")
        }
        reader.read(0).flush()
        AudioArtwork(bytes, mime)
    }

    fun thumbnail(artwork: AudioArtwork): BufferedImage = image(artwork.bytes) { reader ->
        val scale = (maxOf(reader.getWidth(0), reader.getHeight(0)) / 240).coerceAtLeast(1)
        val params = reader.defaultReadParam.apply { setSourceSubsampling(scale, scale, 0, 0) }
        reader.read(0, params)
    }

    private fun <T> image(bytes: ByteArray, action: (javax.imageio.ImageReader) -> T): T {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "图片为空或超过 20 MiB。" }
        return MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: error("无法读取图片，请选择有效的 JPEG 或 PNG。")
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) { "封面尺寸过大，最多支持 2500 万像素。" }
                action(reader)
            } finally { reader.dispose() }
        }
    }
}
