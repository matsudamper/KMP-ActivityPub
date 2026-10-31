package net.matsudamper.kmp.activitypub.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import net.matsudamper.kotpub.activitypub.ActivityPubContentTypes
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.http.EndpointResponse
import net.matsudamper.kotpub.http.RequestHeaders
import net.matsudamper.kotpub.inbox.IncomingInboxRequest

internal fun Route.federationRoutes(federation: Federation) {
    get("/.well-known/webfinger") {
        // ActorDirectory が DB を引く
        val response = withContext(Dispatchers.IO) { federation.webFingerEndpoint.get(call.request.queryParameters["resource"]) }
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

    remoteIconProxyRoutes(federation.remoteIconProxy)

    get("/users/{username}/followers") {
        val response =
            withContext(Dispatchers.IO) {
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
            withContext(Dispatchers.IO) {
                federation.outboxEndpoint.get(
                    call.parameters["username"],
                    call.request.header(HttpHeaders.Accept),
                    call.request.queryParameters[COLLECTION_CURSOR_PARAM],
                )
            }
        call.respondEndpoint(response)
    }

    get("/notes/{publicId}") {
        val response =
            withContext(Dispatchers.IO) {
                federation.noteEndpoint.get(call.parameters["publicId"], call.request.header(HttpHeaders.Accept))
            }
        call.respondEndpoint(response)
    }

    post("/users/{username}/inbox") {
        val request = call.toIncomingInboxRequest()
        val response =
            withContext(Dispatchers.IO) {
                federation.inboxEndpoint.receiveForAccount(username = call.parameters["username"], request = request)
            }
        call.respondEndpoint(response)
    }

    post(ActorUrls.SHARED_INBOX_PATH) {
        val request = call.toIncomingInboxRequest()
        val response = withContext(Dispatchers.IO) { federation.inboxEndpoint.receiveShared(request) }
        call.respondEndpoint(response)
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
        withContext(Dispatchers.IO) {
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

private suspend fun ApplicationCall.respondEndpoint(response: EndpointResponse) {
    response.headers.forEach { (name, value) -> this.response.header(name, value) }
    respondBytes(
        bytes = response.body,
        contentType = ContentType.parse(response.contentType),
        status = HttpStatusCode.fromValue(response.status),
    )
}
