package net.matsudamper.kmp.activitypub.federation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxy
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxyResult

/**
 * Cloudflare に edge でキャッシュさせる。Set-Cookie や Vary を付けるとキャッシュされなくなるので付けない
 */
internal fun Route.remoteImageProxyRoutes(proxy: RemoteImageProxy) {
    get("${RemoteImageProxy.PATH_PREFIX}/{encodedUrl}/{signature}${RemoteImageProxy.PATH_SUFFIX}") {
        proxy.fetch(
            encodedUrl = call.parameters["encodedUrl"].orEmpty(),
            signature = call.parameters["signature"].orEmpty(),
        ) { result ->
            respondRemoteImage(call = call, result = result)
        }
    }
}

private suspend fun respondRemoteImage(
    call: ApplicationCall,
    result: RemoteImageProxyResult,
) {
    call.response.header(CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
    when (result) {
        is RemoteImageProxyResult.Found -> {
            val cacheControl =
                if (call.request.queryParameters.contains("v")) {
                    "public, max-age=31536000, immutable"
                } else {
                    "public, max-age=86400"
                }
            call.response.header(HttpHeaders.CacheControl, cacheControl)
            call.respondBytesWriter(
                contentType = ContentType.parse(result.contentType),
                status = HttpStatusCode.OK,
                contentLength = result.bytes.size.toLong(),
            ) {
                writeInChunks(result.bytes)
            }
        }

        // 相手が落ちているだけのこともあるので、失敗を edge に残さない
        RemoteImageProxyResult.Unavailable -> {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.BadGateway)
        }

        RemoteImageProxyResult.Busy -> {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS_WHEN_BUSY.toString())
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.ServiceUnavailable)
        }

        RemoteImageProxyResult.NotIssued -> {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
        }
    }
}

/**
 * 一度に書くと配列の写しが丸ごと読み手のバッファへ移り、送り終える前に書き手が再開して枠を手放す。
 * 区切って書けば、ByteChannel は溜まりが上限（1MB）に達すると読み手が受け取るまで書き手を止めるので、
 * 枠を手放した後に残るのは数MBに収まる
 */
private suspend fun ByteWriteChannel.writeInChunks(bytes: ByteArray) {
    for (start in bytes.indices step RESPONSE_CHUNK_BYTES) {
        writeFully(bytes, startIndex = start, endIndex = minOf(start + RESPONSE_CHUNK_BYTES, bytes.size))
        flush()
    }
}

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val RETRY_AFTER_SECONDS_WHEN_BUSY = 5
private const val RESPONSE_CHUNK_BYTES = 64 * 1024
