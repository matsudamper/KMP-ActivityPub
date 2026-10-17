package net.matsudamper.kmp.activitypub.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.http.EndpointResponse
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint

internal fun Route.federationRoutes(
    accountService: AccountService,
    localDomain: String,
) {
    val directory = ActorDirectory(domain = localDomain, stored = AccountActorNames(accountService))
    val webFingerEndpoint = WebFingerEndpoint(directory)
    val actorEndpoint =
        ActorEndpoint(
            directory = directory,
            publicKeys = AccountActorPublicKeys(accountService),
            appearances = EmptyActorAppearances,
            profiles = EmptyActorProfiles,
            webPages = null,
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
}

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
