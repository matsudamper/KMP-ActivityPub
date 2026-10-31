package net.matsudamper.kmp.activitypub.federation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.logic.RemoteIconProxy
import net.matsudamper.kmp.activitypub.logic.RemoteIconProxyResult

/**
 * Cloudflare に edge でキャッシュさせる。Set-Cookie や Vary を付けるとキャッシュされなくなるので付けない
 */
internal fun Route.remoteIconProxyRoutes(proxy: RemoteIconProxy) {
    get("${RemoteIconProxy.PATH_PREFIX}/{encodedUrl}/{signature}${RemoteIconProxy.PATH_SUFFIX}") {
        val result =
            proxy.fetch(
                encodedUrl = call.parameters["encodedUrl"].orEmpty(),
                signature = call.parameters["signature"].orEmpty(),
            )
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
        when (result) {
            is RemoteIconProxyResult.Found -> {
                // 相手が同じ URL のまま画像を差し替えることもあるので、1 日で取り直す
                // TODO: サーバー内で保持しているユーザーはそちらを返す
                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                call.respondBytes(bytes = result.bytes, contentType = ContentType.parse(result.contentType), status = HttpStatusCode.OK)
            }

            // 相手が落ちているだけのこともあるので、失敗を edge に残さない
            RemoteIconProxyResult.Unavailable -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.BadGateway)
            }

            RemoteIconProxyResult.NotIssued -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
            }
        }
    }
}

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
