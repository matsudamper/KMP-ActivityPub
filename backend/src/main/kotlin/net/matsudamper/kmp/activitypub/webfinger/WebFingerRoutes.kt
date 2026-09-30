package net.matsudamper.kmp.activitypub.webfinger

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.activitypub.actor.ActorDirectory
import net.matsudamper.activitypub.webfinger.WebFingerEndpoint
import net.matsudamper.kmp.activitypub.http.respondEndpoint

internal fun Route.webFingerRoutes(directory: ActorDirectory) {
    val endpoint = WebFingerEndpoint(directory)
    get("/.well-known/webfinger") {
        call.respondEndpoint(endpoint.get(resource = call.request.queryParameters["resource"]))
    }
}
