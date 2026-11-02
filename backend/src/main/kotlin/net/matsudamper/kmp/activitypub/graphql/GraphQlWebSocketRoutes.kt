package net.matsudamper.kmp.activitypub.graphql

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext

/**
 * https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
 */
private const val GRAPHQL_TRANSPORT_WS = "graphql-transport-ws"

private val CONNECTION_INIT_TIMEOUT = 10.seconds

internal fun Route.graphQlWebSocketRoutes(engine: GraphQlEngine) {
    webSocket(GRAPHQL_PATH, protocol = GRAPHQL_TRANSPORT_WS) {
        // ブラウザは別オリジンからの接続にも Cookie を付けるので、POST の Content-Type の代わりに Origin で断る
        if (!isSameOrigin(call)) {
            close(CloseReason(4403, "Forbidden"))
            return@webSocket
        }
        GraphQlWebSocketConnection(this, engine).serve()
    }
}

/**
 * Origin を付けないのはブラウザ以外なので通す
 */
private fun isSameOrigin(call: ApplicationCall): Boolean {
    val origin = call.request.headers[HttpHeaders.Origin] ?: return true
    val host = call.request.headers[HttpHeaders.Host] ?: return false
    val originAuthority = runCatching { URI(origin).authority }.getOrNull() ?: return false
    return originAuthority.equals(host, ignoreCase = true)
}

private class GraphQlWebSocketConnection(
    private val session: DefaultWebSocketServerSession,
    private val engine: GraphQlEngine,
) {
    private val operations = ConcurrentHashMap<String, Job>()

    suspend fun serve() {
        val initialized = withTimeoutOrNull(CONNECTION_INIT_TIMEOUT) {
            receiveMessage()?.type == "connection_init"
        }
        if (initialized != true) {
            session.close(CloseReason(4408, "Connection initialisation timeout"))
            return
        }
        send(message("connection_ack", null, null))

        val closeReason = coroutineScope {
            val reason = receiveOperations()
            coroutineContext.cancelChildren()
            reason
        }
        if (closeReason != null) session.close(closeReason)
    }

    /**
     * 相手が閉じたら null を返す。protocol 違反なら閉じる理由を返す
     */
    private suspend fun CoroutineScope.receiveOperations(): CloseReason? {
        while (true) {
            val message = receiveMessage() ?: return null
            when (message.type) {
                "ping" -> send(message("pong", null, null))

                "pong" -> Unit

                "subscribe" -> {
                    val id = message.id ?: return CloseReason(4400, "id が無い")
                    val payload = message.payload as? JsonObject ?: return CloseReason(4400, "payload が無い")
                    val request = runCatching { ServerJson.value.decodeFromJsonElement(GraphQlRequest.serializer(), payload) }
                        .getOrElse { return CloseReason(4400, "GraphQL のリクエストとして読めない") }
                    if (operations.containsKey(id)) return CloseReason(4409, "Subscriber for $id already exists")
                    val job = launch(start = CoroutineStart.LAZY) { runOperation(id, request) }
                    operations[id] = job
                    job.invokeOnCompletion { operations.remove(id, job) }
                    job.start()
                }

                "complete" -> {
                    val id = message.id
                    if (id != null) operations.remove(id)?.cancel()
                }

                "connection_init" -> return CloseReason(4429, "Too many initialisation requests")

                else -> return CloseReason(4400, "知らない type")
            }
        }
    }

    private suspend fun runOperation(id: String, request: GraphQlRequest) {
        try {
            // graphql-java の execute は blocking なので、ルートのスレッドから外す
            when (val subscription = withOpenTelemetryContext { engine.subscribe(request, session.call) }) {
                is GraphQlSubscription.Rejected -> send(message("error", id, subscription.errors))

                is GraphQlSubscription.Stream -> {
                    subscription.results.collect { send(message("next", id, it)) }
                    send(message("complete", id, null))
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            TraceLogger.impl().noticeThrowable(failure, isError = true)
            send(message("error", id, ServerJson.value.parseToJsonElement("""[{"message":"内部エラー"}]""")))
        }
    }

    private suspend fun receiveMessage(): ClientMessage? {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val json = runCatching { ServerJson.value.parseToJsonElement(frame.readText()) }.getOrNull() as? JsonObject
            return ClientMessage(
                type = (json?.get("type") as? JsonPrimitive)?.contentOrNull,
                id = (json?.get("id") as? JsonPrimitive)?.contentOrNull,
                payload = json?.get("payload"),
            )
        }
        return null
    }

    private suspend fun send(message: JsonObject) {
        session.send(Frame.Text(ServerJson.value.encodeToString(JsonObject.serializer(), message)))
    }

    private fun message(type: String, id: String?, payload: JsonElement?): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive(type))
            if (id != null) put("id", JsonPrimitive(id))
            if (payload != null) put("payload", payload)
        }

    private class ClientMessage(
        val type: String?,
        val id: String?,
        val payload: JsonElement?,
    )
}
