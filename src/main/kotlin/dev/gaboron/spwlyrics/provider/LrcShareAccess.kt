package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.provider.credential.GeneratedCredential
import java.net.URI

/** Keeps credential decoding at the fixed-origin request boundary, without a plaintext getter. */
internal object LrcShareAccess {
    val available: Boolean get() = GeneratedCredential.available()
    val identity: String get() = GeneratedCredential.identity()

    fun request(http: ProviderHttpClient, path: String, session: String?): String {
        val uri = URI.create("https://api.lrcshare.com/v1$path")
        require(uri.scheme == "https" && uri.host == "api.lrcshare.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.path == "/v1/catalog" || uri.path == "/v1/search" || uri.path.startsWith("/v1/song/")))
        check(available) { "此构建未包含 LrcShare 凭据，请使用正式安装包。" }
        return GeneratedCredential.request(http, path, session)
    }
}
