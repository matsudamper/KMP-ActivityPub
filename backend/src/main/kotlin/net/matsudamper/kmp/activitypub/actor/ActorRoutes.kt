package net.matsudamper.kmp.activitypub.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.activitypub.actor.ActorDirectory
import net.matsudamper.activitypub.actor.ActorEndpoint
import net.matsudamper.activitypub.actor.ActorKey
import net.matsudamper.activitypub.actor.StoredActorProfiles
import net.matsudamper.activitypub.actor.StoredFeedLinks
import net.matsudamper.activitypub.url.WebPageUrls
import net.matsudamper.kmp.activitypub.http.respondEndpoint

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorKey,
    feedLinks: StoredFeedLinks,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
) {
    val endpoint = ActorEndpoint(directory, actorKey, feedLinks, profiles, webPages)
    get("/users/{username}") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}
