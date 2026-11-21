package net.matsudamper.kmp.activitypub.logic

import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 他のサーバーの画像を、このサーバーを通して配る。
 * 画面から相手のサーバーへ直接取りに行くと、Web では CORS を返さないサーバーの画像を読めない。
 *
 * このサーバーが発行した URL だけを中継する。誰の指定でも取りに行くと、このサーバーを踏み台にされる
 *
 * @param origin `https://example.com` の形。末尾に / を付けない
 */
class RemoteImageProxy(
    private val origin: String,
    private val signingKey: ByteArray,
    private val fetcher: RemoteImageFetcher,
) {
    /**
     * http と https 以外は取りに行けないので null
     */
    fun proxyUrlOf(remoteUrl: String): String? = proxyUrlOf(remoteUrl = remoteUrl, updatedAt = null)

    fun proxyUrlOf(remoteUrl: String, updatedAt: Instant?): String? {
        val scheme = runCatching { URI(remoteUrl).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val encodedUrl = base64Url.encodeToString(remoteUrl.encodeToByteArray())
        val version = if (updatedAt == null) "" else "?v=${base64Url.encodeToString(updatedAt.toString().encodeToByteArray())}"
        return "$origin$PATH_PREFIX/$encodedUrl/${sign(remoteUrl)}$PATH_SUFFIX$version"
    }

    suspend fun <T> fetch(
        encodedUrl: String,
        signature: String,
        respond: suspend (RemoteImageProxyResult) -> T,
    ): T {
        val remoteUrl =
            runCatching { Base64.getUrlDecoder().decode(encodedUrl).decodeToString() }.getOrNull()
                ?: return respond(RemoteImageProxyResult.NotIssued)
        val signatureMatches = MessageDigest.isEqual(sign(remoteUrl).encodeToByteArray(), signature.encodeToByteArray())
        if (!signatureMatches) return respond(RemoteImageProxyResult.NotIssued)

        val body = fetcher.open(remoteUrl) ?: return respond(RemoteImageProxyResult.Unavailable)
        return body.use {
            // 応答を書き始める前の失敗なので、取れなかったこととして返せる
            val result =
                try {
                    inspect(body)
                } catch (_: IOException) {
                    RemoteImageProxyResult.Unavailable
                }
            respond(result)
        }
    }

    /**
     * 応答を書き始めた後は失敗を返せないので、形式と大きさは先頭を読んだ時点で確かめる
     */
    private suspend fun inspect(body: RemoteImageBody): RemoteImageProxyResult {
        val contentLength = body.contentLength
        if (contentLength != null && contentLength > MAX_IMAGE_BYTES) return RemoteImageProxyResult.Unavailable
        val head = body.readHead(ImageFormat.SIGNATURE_BYTES)
        val contentType = ImageFormat.detect(head)?.contentType
            ?: ICO_CONTENT_TYPE.takeIf { head.isIco() }
            ?: return RemoteImageProxyResult.Unavailable
        return RemoteImageProxyResult.Found(
            contentType = contentType,
            contentLength = contentLength,
            head = head,
            rest = body,
            maxBytes = MAX_IMAGE_BYTES,
        )
    }

    /**
     * favicon は ICO が多いので中継では通す。投稿やプロフィールの画像としては受け付けないので [ImageFormat] には入れない
     */
    private fun ByteArray.isIco(): Boolean =
        size >= ICO_SIGNATURE.size && ICO_SIGNATURE.indices.all { index -> this[index] == ICO_SIGNATURE[index] }

    private fun sign(remoteUrl: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey, HMAC_ALGORITHM))
        return base64Url.encodeToString(mac.doFinal(remoteUrl.encodeToByteArray()))
    }

    companion object {
        const val PATH_PREFIX: String = "/proxy/image"

        /**
         * Cloudflare は拡張子でキャッシュするかを決める。.bin は既定でキャッシュする拡張子に入っていて、画像の形式を名乗らずに済む
         */
        const val PATH_SUFFIX: String = ".bin"

        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val ICO_CONTENT_TYPE = "image/x-icon"
        private val ICO_SIGNATURE = byteArrayOf(0x00, 0x00, 0x01, 0x00)

        /**
         * 同じ実装の別のサーバーが配る投稿の画像も表示できるよう、こちらで受け付ける投稿の画像の上限に揃える
         */
        private const val MAX_IMAGE_BYTES = NotePostService.MAX_IMAGE_BYTES
        private val base64Url = Base64.getUrlEncoder().withoutPadding()
    }
}

fun interface RemoteImageFetcher {
    /**
     * 取れなければ null。返した本文は呼び出し側が閉じる
     */
    suspend fun open(url: String): RemoteImageBody?
}

interface RemoteImageBody : AutoCloseable {
    /**
     * 相手が名乗った大きさ。名乗っていなければ null
     */
    val contentLength: Long?

    /**
     * [buffer] に読んだバイト数。終わりなら -1
     */
    suspend fun read(buffer: ByteArray): Int
}

private suspend fun RemoteImageBody.readHead(size: Int): ByteArray {
    var head = ByteArray(0)
    while (head.size < size) {
        val chunk = ByteArray(size - head.size)
        val read = read(chunk)
        if (read < 0) break
        head += chunk.copyOf(read)
    }
    return head
}

sealed interface RemoteImageProxyResult {
    /**
     * 本文は相手から読みながら流す。画像を丸ごとメモリに載せない
     */
    class Found(
        val contentType: String,
        val contentLength: Long?,
        private val head: ByteArray,
        private val rest: RemoteImageBody,
        private val maxBytes: Int,
    ) : RemoteImageProxyResult {
        /**
         * 上限を超えたら [IOException] で打ち切る。応答を書き始めた後なので、接続を切るしかない
         */
        suspend fun copyTo(write: suspend (buffer: ByteArray, length: Int) -> Unit) {
            write(head, head.size)
            var total = head.size.toLong()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = rest.read(buffer)
                if (read < 0) return
                total += read
                if (total > maxBytes) throw IOException("画像が上限 $maxBytes バイトを超えた")
                write(buffer, read)
            }
        }

        private companion object {
            const val COPY_BUFFER_BYTES = 64 * 1024
        }
    }

    /**
     * このサーバーが発行していない URL
     */
    data object NotIssued : RemoteImageProxyResult

    /**
     * 相手から取れないか、受け付ける画像の形式ではない
     */
    data object Unavailable : RemoteImageProxyResult
}
