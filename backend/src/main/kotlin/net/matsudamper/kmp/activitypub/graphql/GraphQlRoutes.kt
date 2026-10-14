package net.matsudamper.kmp.activitypub.graphql

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import io.ktor.utils.io.readBuffer
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlBadRequest
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.json.respondJson

internal const val GRAPHQL_PATH = "/graphql"

private const val MAX_BODY_BYTES = 1024 * 1024

/**
 * graphql-java の実行はブロックする。受付スレッドを塞がない
 */
private val GRAPHQL_DISPATCHER: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1))

internal fun Route.graphQlRoutes(engine: GraphQlEngine) {
    post(GRAPHQL_PATH) {
        // text/plain などはプリフライト無しで別オリジンから送れる
        if (!call.request.contentType().match(ContentType.Application.Json)) {
            call.respondText("Content-Type は application/json にすること", status = HttpStatusCode.UnsupportedMediaType)
            return@post
        }

        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        // chunked は上の判定をすり抜ける
        val bodyBytes = call.receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
        if (bodyBytes.size > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }
        val body = bodyBytes.decodeToString()

        val request =
            runCatching {
                ServerJson.value.decodeFromString(GraphQlRequest.serializer(), body)
            }.getOrElse {
                call.respondJson(
                    GraphQlBadRequest.serializer(),
                    GraphQlBadRequest("GraphQL のリクエストとして読めない"),
                    status = HttpStatusCode.BadRequest,
                )
                return@post
            }

        val result = withContext(GRAPHQL_DISPATCHER) { engine.execute(request) }

        call.respondJson(JsonObject.serializer(), result)
    }
}
