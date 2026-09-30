package net.matsudamper.kmp.activitypub.graphql

import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import net.matsudamper.activitypub.json.AppJson
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlBadRequest
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.json.respondJson
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

private const val MAX_BODY_BYTES = 1024 * 1024

internal fun Route.graphQlRoutes(engine: GraphQlEngine) {
    post(GRAPHQL_PATH) {
        // text/plain などの CORS safelist の型はプリフライト無しで別オリジンから Cookie 付きで送れる。
        // SameSite=Strict は同じ site の別オリジンからの送信を止めないので、JSON 以外は受けずにプリフライトを必須にする
        if (!call.request.contentType().match(ContentType.Application.Json)) {
            call.respondText("Content-Type は application/json にすること", status = HttpStatusCode.UnsupportedMediaType)
            return@post
        }

        // 読んでから確かめても、その時点で受け取り終えている
        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        // Content-Length の無い chunked のボディは上の判定をすり抜けるので、上限の 1 バイト先までで読むのを止める
        val bodyBytes = call.receiveChannel().readRemaining(MAX_BODY_BYTES + 1L).readByteArray()
        if (bodyBytes.size > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }
        val body = bodyBytes.decodeToString()

        val request = runCatching {
            AppJson.decodeFromString(GraphQlRequest.serializer(), body)
        }
            .getOrElse {
                call.respondJson(
                    GraphQlBadRequest.serializer(),
                    GraphQlBadRequest("GraphQL のリクエストとして読めない"),
                    status = HttpStatusCode.BadRequest,
                )
                return@post
            }

        // パスワードの照合で PBKDF2 を回すので、そのまま実行すると他が詰まる
        val result = engine.execute(request, call)

        call.respondJson(JsonObject.serializer(), result)
    }
}
