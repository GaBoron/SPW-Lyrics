package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.AudioArtworkImages
import dev.gaboron.spwlyrics.domain.AudioArtwork
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

/** Image downloads happen only for a selected metadata candidate, outside lyric matching. */
internal class CoverArtworkClient {
    fun load(url: String): AudioArtwork {
        var uri = URI.create(url)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        repeat(6) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Cover download cancelled")
            require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "封面地址无效。" }
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtMost(5000).toInt()
            check(remaining > 0) { "封面下载超时，请重试。" }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = remaining
                connection.readTimeout = remaining
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", ProviderHttpClient.USER_AGENT)
                connection.setRequestProperty("Accept", "image/jpeg,image/png")
                if (uri.host.endsWith("qq.com")) connection.setRequestProperty("Referer", "https://y.qq.com/")
                when (val status = connection.responseCode) {
                    in 300..399 -> uri = uri.resolve(checkNotNull(connection.getHeaderField("Location")) { "封面重定向地址缺失。" })
                    in 200..299 -> {
                        require(connection.contentLengthLong <= AudioArtworkImages.MAX_BYTES) { "在线封面超过 20 MiB。" }
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                check(System.nanoTime() < deadline) { "封面下载超时，请重试。" }
                                if (Thread.currentThread().isInterrupted) throw InterruptedException("Cover download cancelled")
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= AudioArtworkImages.MAX_BYTES) { "在线封面超过 20 MiB。" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        return AudioArtworkImages.decode(bytes)
                    }
                    404 -> error("此候选没有可用封面，可更换来源或从本地选择。")
                    else -> error("封面下载失败（HTTP $status），请重试或更换来源。")
                }
            } finally { connection.disconnect() }
        }
        error("封面重定向次数过多，请更换来源。")
    }
}
