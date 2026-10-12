package net.matsudamper.kmp.activitypub.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.http.respondEndpoint
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorKey
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.url.WebPageUrls

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorKey,
    appearances: StoredActorAppearances,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
) {
    val endpoint = ActorEndpoint(directory, actorKey, appearances, profiles, webPages)
    get("/users/{username}") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}
