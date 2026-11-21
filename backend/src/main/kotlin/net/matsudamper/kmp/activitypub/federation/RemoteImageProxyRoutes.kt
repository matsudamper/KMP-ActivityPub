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
                contentLength = result.contentLength,
            ) {
                result.copyTo { buffer, length -> writeFully(buffer, startIndex = 0, endIndex = length) }
            }
        }

        // 相手が落ちているだけのこともあるので、失敗を edge に残さない
        RemoteImageProxyResult.Unavailable -> {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.BadGateway)
        }

        RemoteImageProxyResult.NotIssued -> {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
        }
    }
}

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
