package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 他のサーバーのプロフィール画像を、このサーバーを通して配る。
 * 画面から相手のサーバーへ直接取りに行くと、Web では CORS を返さないサーバーの画像を読めない。
 *
 * このサーバーが発行した URL だけを中継する。誰の指定でも取りに行くと、このサーバーを踏み台にされる
 *
 * @param origin `https://example.com` の形。末尾に / を付けない
 */
class RemoteIconProxy(
    private val origin: String,
    private val signingKey: ByteArray,
    private val fetcher: RemoteIconFetcher,
) {
    /**
     * http と https 以外は取りに行けないので null
     */
    fun proxyUrlOf(remoteUrl: String): String? {
        val scheme = runCatching { URI(remoteUrl).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val encodedUrl = base64Url.encodeToString(remoteUrl.encodeToByteArray())
        return "$origin$PATH_PREFIX/$encodedUrl/${sign(remoteUrl)}$PATH_SUFFIX"
    }

    suspend fun fetch(
        encodedUrl: String,
        signature: String,
    ): RemoteIconProxyResult {
        val remoteUrl =
            runCatching { Base64.getUrlDecoder().decode(encodedUrl).decodeToString() }.getOrNull()
                ?: return RemoteIconProxyResult.NotIssued
        val signatureMatches = MessageDigest.isEqual(sign(remoteUrl).encodeToByteArray(), signature.encodeToByteArray())
        if (!signatureMatches) return RemoteIconProxyResult.NotIssued

        val bytes = fetcher.fetch(url = remoteUrl, maxBytes = MAX_ICON_BYTES) ?: return RemoteIconProxyResult.Unavailable
        val format = ProfileIconFormat.detect(bytes) ?: return RemoteIconProxyResult.Unavailable
        return RemoteIconProxyResult.Found(bytes = bytes, contentType = format.contentType)
    }

    private fun sign(remoteUrl: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey, HMAC_ALGORITHM))
        return base64Url.encodeToString(mac.doFinal(remoteUrl.encodeToByteArray()))
    }

    companion object {
        const val PATH_PREFIX: String = "/proxy/icon"

        /**
         * Cloudflare は拡張子でキャッシュするかを決める。.bin は既定でキャッシュする拡張子に入っていて、画像の形式を名乗らずに済む
         */
        const val PATH_SUFFIX: String = ".bin"

        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val MAX_ICON_BYTES = 5 * 1024 * 1024
        private val base64Url = Base64.getUrlEncoder().withoutPadding()
    }
}

fun interface RemoteIconFetcher {
    /**
     * 取れなかったときと [maxBytes] を超えたときは null
     */
    suspend fun fetch(
        url: String,
        maxBytes: Int,
    ): ByteArray?
}

sealed interface RemoteIconProxyResult {
    class Found(
        val bytes: ByteArray,
        val contentType: String,
    ) : RemoteIconProxyResult

    /**
     * このサーバーが発行していない URL
     */
    data object NotIssued : RemoteIconProxyResult

    /**
     * 相手から取れないか、受け付ける画像の形式ではない
     */
    data object Unavailable : RemoteIconProxyResult
}
