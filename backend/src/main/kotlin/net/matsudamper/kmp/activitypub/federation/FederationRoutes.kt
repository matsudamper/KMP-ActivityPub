package net.matsudamper.kmp.activitypub.federation

import kotlinx.io.readByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptItems
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.activitypub.ActivityPubContentTypes
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.http.EndpointResponse
import net.matsudamper.kotpub.http.RequestHeaders
import net.matsudamper.kotpub.inbox.InboxHandling
import net.matsudamper.kotpub.inbox.InboxResult
import net.matsudamper.kotpub.inbox.IncomingInboxRequest
import org.slf4j.LoggerFactory

internal fun Route.federationRoutes(federation: Federation) {
    get("/.well-known/webfinger") {
        // ActorDirectory が DB を引く
        val response = withOpenTelemetryContext { federation.webFingerEndpoint.get(call.request.queryParameters["resource"]) }
        call.respondEndpoint(response)
    }

    get("/.well-known/nodeinfo") {
        call.respondEndpoint(federation.nodeInfoEndpoint.discovery())
    }

    get("/nodeinfo/2.1") {
        // ユーザー数・投稿数を DB で数える
        val response = withOpenTelemetryContext { federation.nodeInfoEndpoint.nodeInfo() }
        call.respondEndpoint(response)
    }

    get("/users/{username}") {
        call.respondActor(federation.actorEndpoint)
    }

    // Mastodon と同じく、プロフィールページの URL でも ActivityPub の取得なら Actor を返す
    createChild(ActivityPubAcceptRouteSelector).get("/@{username}") {
        call.respondActor(federation.actorEndpoint)
    }

    val actorIconEndpoint = federation.actorIconEndpoint
    if (actorIconEndpoint != null) {
        get("/users/{username}${ActorUrls.ICON_PATH}") {
            val response = actorIconEndpoint.get(username = call.parameters["username"], version = call.request.queryParameters["v"])
            call.respondEndpoint(response)
        }
    }

    remoteImageProxyRoutes(federation.remoteImageProxy)
    val noteImageFiles = federation.noteImageFiles
    if (noteImageFiles != null) {
        noteImageRoutes(noteImageFiles)
    }

    get("/users/{username}/followers") {
        val response =
            withOpenTelemetryContext {
                federation.followerEndpoint.get(
                    username = call.parameters["username"],
                    accept = call.request.header(HttpHeaders.Accept),
                    cursor = call.request.queryParameters["cursor"],
                )
            }
        call.respondEndpoint(response)
    }

    get("/users/{username}/outbox") {
        val response =
            withOpenTelemetryContext {
                federation.outboxEndpoint.get(
                    call.parameters["username"],
                    call.request.header(HttpHeaders.Accept),
                    call.request.queryParameters[COLLECTION_CURSOR_PARAM],
                )
            }
        call.respondEndpoint(response)
    }

    get("/users/{username}/collections/featured") {
        val response =
            withOpenTelemetryContext {
                federation.featuredEndpoint.get(call.parameters["username"], call.request.header(HttpHeaders.Accept))
            }
        call.respondEndpoint(response)
    }

    get("/notes/{publicId}") {
        val response =
            withOpenTelemetryContext {
                federation.noteEndpoint.get(call.parameters["publicId"], call.request.header(HttpHeaders.Accept))
            }
        call.respondEndpoint(response)
    }

    post("/users/{username}/inbox") {
        val request = call.toIncomingInboxRequest()
        val response =
            withOpenTelemetryContext {
                federation.inboxEndpoint.receiveForAccount(username = call.parameters["username"], request = request)
            }
        logInboxResult(response.result)
        call.respondEndpoint(response.response)
    }

    post(ActorUrls.SHARED_INBOX_PATH) {
        val request = call.toIncomingInboxRequest()
        val response = withOpenTelemetryContext { federation.inboxEndpoint.receiveShared(request) }
        logInboxResult(response.result)
        call.respondEndpoint(response.response)
    }
}

/**
 * ブラウザも Accept にワイルドカードを含めるので、Ktor の accept() ではブラウザまで一致してしまう
 */
private object ActivityPubAcceptRouteSelector : RouteSelector() {
    private val activityPubContentTypes =
        setOf(
            ActivityPubContentTypes.ActivityJson.toString(),
            ActivityPubContentTypes.LdJson.toString(),
        )

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val acceptsActivityPub = context.call.request.acceptItems().any { it.value in activityPubContentTypes }
        return if (acceptsActivityPub) RouteSelectorEvaluation.Constant else RouteSelectorEvaluation.Failed
    }
}

private suspend fun ApplicationCall.respondActor(actorEndpoint: ActorEndpoint) {
    // ActorDirectory が DB を引く
    val response =
        withOpenTelemetryContext {
            actorEndpoint.get(username = parameters["username"], accept = request.header(HttpHeaders.Accept))
        }
    respondEndpoint(response)
}

private fun ApplicationCall.toIncomingInboxRequest(): IncomingInboxRequest =
    IncomingInboxRequest(
        method = request.local.method.value,
        // 署名されたのはリクエストラインの綴りなので、組み立て直さずにそのまま渡す
        requestTarget = request.uri,
        headers = RequestHeaders.of(request.headers.entries().associate { (name, values) -> name to values }),
        readBody = { maxBytes -> receiveChannel().readRemaining(maxBytes + 1L).readByteArray() },
    )

private val inboxLogger = LoggerFactory.getLogger("net.matsudamper.kmp.activitypub.federation.Inbox")

/**
 * kotpub は受け付けなかった理由をログに残さずに結果で返すので、こちらで残す
 *
 * @param result 宛先のアカウントが無いなど、処理まで進まなかったときは null
 */
private fun logInboxResult(result: InboxResult?) {
    when (result) {
        null -> Unit

        is InboxResult.Accepted -> {
            val handling = result.handling
            if (handling is InboxHandling.Ignored) inboxLogger.info("inbox で受け付けなかった: ${handling.reason}")
        }

        is InboxResult.Unauthorized -> inboxLogger.warn("inbox の署名を拒否した: ${result.reason}")

        is InboxResult.BadRequest -> inboxLogger.warn("inbox のボディを読めなかった: ${result.reason}")

        is InboxResult.TemporarilyUnavailable -> inboxLogger.warn("inbox を処理できなかったので送り直してもらう", result.cause)
    }
}

private suspend fun ApplicationCall.respondEndpoint(response: EndpointResponse) {
    response.headers.forEach { (name, value) -> this.response.header(name, value) }
    respondBytes(
        bytes = response.body,
        contentType = ContentType.parse(response.contentType),
        status = HttpStatusCode.fromValue(response.status),
    )
}
