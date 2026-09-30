package net.matsudamper.kmp.activitypub.nodeinfo

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.activitypub.nodeinfo.NodeInfoEndpoint
import net.matsudamper.activitypub.nodeinfo.NodeInfoSoftware
import net.matsudamper.kmp.activitypub.http.respondEndpoint

internal fun Route.nodeInfoRoutes(domain: String) {
    val endpoint =
        NodeInfoEndpoint(
            domain = domain,
            // version は build.gradle.kts の allprojects.version と合わせる。自動で追従はしない
            software =
            NodeInfoSoftware(
                name = "kmp-activitypub",
                version = "0.1.0",
                repository = "https://github.com/matsudamper/kmp-activitypub",
            ),
        )
    get("/.well-known/nodeinfo") {
        call.respondEndpoint(endpoint.discovery())
    }
    get("/nodeinfo/2.1") {
        call.respondEndpoint(endpoint.nodeInfo())
    }
}
