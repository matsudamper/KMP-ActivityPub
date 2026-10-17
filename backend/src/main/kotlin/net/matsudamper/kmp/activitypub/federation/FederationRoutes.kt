package net.matsudamper.kmp.activitypub.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.federation.data.ActorResponse
import net.matsudamper.kmp.activitypub.federation.data.WebFingerResponse
import net.matsudamper.kmp.activitypub.json.respondJson
import net.matsudamper.kmp.activitypub.logic.AccountService

private val ActivityJson = ContentType("application", "activity+json")
private val JrdJson = ContentType("application", "jrd+json")

internal fun Route.federationRoutes(
    accountService: AccountService,
    urls: FederationUrls,
) {
    get("/.well-known/webfinger") {
        val resource = call.request.queryParameters["resource"]
        if (resource == null) {
            call.respondText("resource を指定すること", status = HttpStatusCode.BadRequest)
            return@get
        }

        val requestedUsername = urls.usernameOf(resource)
        val account = requestedUsername?.let { withContext(Dispatchers.IO) { accountService.account(it) } }
        if (account == null) {
            call.respondText("見つからない: $resource", status = HttpStatusCode.NotFound)
            return@get
        }

        val actorUrl = urls.actor(account.username)
        val profileUrl = urls.profilePage(account.username)
        call.respondJson(
            WebFingerResponse.serializer(),
            WebFingerResponse(
                subject = urls.acct(account.username),
                aliases = listOf(actorUrl, profileUrl),
                links = listOf(
                    WebFingerResponse.Link(rel = "self", type = ActivityJson.toString(), href = actorUrl),
                    WebFingerResponse.Link(
                        rel = "http://webfinger.net/rel/profile-page",
                        type = ContentType.Text.Html.toString(),
                        href = profileUrl,
                    ),
                ),
            ),
            contentType = JrdJson,
        )
    }

    get("/users/{username}") {
        val requestedUsername = checkNotNull(call.parameters["username"])
        val actor =
            withContext(Dispatchers.IO) {
                val account = accountService.account(requestedUsername) ?: return@withContext null
                val actorUrl = urls.actor(account.username)
                ActorResponse(
                    context = listOf("https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"),
                    id = actorUrl,
                    type = "Person",
                    preferredUsername = account.username,
                    name = account.username,
                    url = urls.profilePage(account.username),
                    inbox = urls.inbox(account.username),
                    publicKey = accountService.publicKeyPem(account.id)?.let { publicKeyPem ->
                        ActorResponse.PublicKey(id = urls.publicKey(account.username), owner = actorUrl, publicKeyPem = publicKeyPem)
                    },
                )
            }
        if (actor == null) {
            call.respondText("見つからない: $requestedUsername", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondJson(ActorResponse.serializer(), actor, contentType = ActivityJson)
    }
}
