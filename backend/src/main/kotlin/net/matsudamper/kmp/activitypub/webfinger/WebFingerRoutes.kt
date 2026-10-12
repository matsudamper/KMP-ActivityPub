package net.matsudamper.kmp.activitypub.webfinger

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.http.respondEndpoint
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint

internal fun Route.webFingerRoutes(directory: ActorDirectory) {
    val endpoint = WebFingerEndpoint(directory)
    get("/.well-known/webfinger") {
        call.respondEndpoint(endpoint.get(resource = call.request.queryParameters["resource"]))
    }
}
