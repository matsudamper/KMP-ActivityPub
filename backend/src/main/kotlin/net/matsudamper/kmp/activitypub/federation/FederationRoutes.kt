package net.matsudamper.kmp.activitypub.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.follower.FollowerEndpoint
import net.matsudamper.kotpub.http.EndpointResponse
import net.matsudamper.kotpub.http.RequestHeaders
import net.matsudamper.kotpub.inbox.InboxEndpoint
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.inbox.IncomingInboxRequest
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint

internal fun Route.federationRoutes(
    accountService: AccountService,
    followerRepository: FollowerRepository,
    remoteActors: RemoteActors,
    localDomain: String,
) {
    val directory = ActorDirectory(domain = localDomain, stored = AccountActorNames(accountService))
    val followers = AccountFollowerStore(followerRepository)
    val webFingerEndpoint = WebFingerEndpoint(directory)
    val actorEndpoint =
        ActorEndpoint(
            directory = directory,
            publicKeys = AccountActorPublicKeys(accountService),
            appearances = EmptyActorAppearances,
            profiles = EmptyActorProfiles,
            webPages = null,
        )
    val followerEndpoint = FollowerEndpoint(directory = directory, followers = followers)
    val inboxEndpoint =
        InboxEndpoint(
            directory = directory,
            service = InboxService.default(
                directory = directory,
                remoteActors = remoteActors,
                followers = followers,
                favourites = UnsupportedFavouriteStore,
                stamps = UnsupportedStampStore,
                earlyUndoneLikes = UnsupportedEarlyUndoneLikes,
                domainBlocks = NoInboxDomainBlocks,
                domain = localDomain,
            ),
        )

    get("/.well-known/webfinger") {
        // ActorDirectory が DB を引く
        val response = withContext(Dispatchers.IO) { webFingerEndpoint.get(call.request.queryParameters["resource"]) }
        call.respondEndpoint(response)
    }

    get("/users/{username}") {
        val response =
            withContext(Dispatchers.IO) {
                actorEndpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept))
            }
        call.respondEndpoint(response)
    }

    get("/users/{username}/followers") {
        val response =
            withContext(Dispatchers.IO) {
                followerEndpoint.get(
                    username = call.parameters["username"],
                    accept = call.request.header(HttpHeaders.Accept),
                    cursor = call.request.queryParameters[COLLECTION_CURSOR_PARAM],
                )
            }
        call.respondEndpoint(response)
    }

    post("/users/{username}/inbox") {
        val response =
            withContext(Dispatchers.IO) {
                inboxEndpoint.receiveForAccount(username = call.parameters["username"], request = call.toIncomingInboxRequest())
            }
        call.respondEndpoint(response)
    }

    post(ActorUrls.SHARED_INBOX_PATH) {
        val response = withContext(Dispatchers.IO) { inboxEndpoint.receiveShared(call.toIncomingInboxRequest()) }
        call.respondEndpoint(response)
    }
}

/**
 * 署名はリクエストラインの綴りに対して計算されているので、パスとクエリは組み直さずに渡す
 */
private fun ApplicationCall.toIncomingInboxRequest(): IncomingInboxRequest =
    IncomingInboxRequest(
        method = request.httpMethod.value,
        requestTarget = request.uri,
        headers = RequestHeaders.of(request.headers.entries().associate { (name, values) -> name to values }),
        readBody = { maxBytes -> receiveChannel().readBuffer(maxBytes + 1L).readByteArray() },
    )

private object EmptyActorAppearances : StoredActorAppearances {
    override fun find(username: String): ActorAppearance = ActorAppearance.EMPTY
}

private object EmptyActorProfiles : StoredActorProfiles {
    override fun find(username: String): ActorProfile = ActorProfile.EMPTY
}

private suspend fun ApplicationCall.respondEndpoint(response: EndpointResponse) {
    response.headers.forEach { (name, value) -> this.response.header(name, value) }
    respondBytes(
        bytes = response.body,
        contentType = ContentType.parse(response.contentType),
        status = HttpStatusCode.fromValue(response.status),
    )
}
