package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.ProviderSearchPhase
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/** Only short, known reasons reach the UI; raw responses and stack traces stay out. */
internal object ProviderSearchFailure {
    fun status(source: LyricsSource, error: Throwable): ProviderSearchStatus {
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        causes.filterIsInstance<ProviderNotConfiguredException>().firstOrNull()?.let {
            return ProviderSearchStatus(source, ProviderSearchPhase.NOT_CONFIGURED, it.message.orEmpty())
        }
        val timeout = causes.any { it is SocketTimeoutException || it is HttpTimeoutException || it is TimeoutException }
        val detail = when {
            timeout -> "请求超时，请稍后重试。"
            causes.any { it is SSLException } -> "TLS 握手失败，请检查网络或代理。"
            causes.any { it is UnknownHostException } -> "域名解析失败，请检查网络。"
            causes.any { it is ConnectException } -> "连接失败，请检查网络或代理。"
            else -> causes.firstNotNullOfOrNull { Regex("HTTP \\d{3}").find(it.message.orEmpty())?.value }
                ?: "接口响应或索引处理失败，请稍后重试。"
        }
        return ProviderSearchStatus(source, if (timeout) ProviderSearchPhase.TIMED_OUT else ProviderSearchPhase.FAILED, detail)
    }
}
