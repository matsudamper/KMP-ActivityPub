package net.matsudamper.kmp.activitypub.federation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.FakeActivityDelivery
import net.matsudamper.kmp.activitypub.FakeRemoteActors
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.ServerEnv
import net.matsudamper.kmp.activitypub.TestDependencies
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.crypto.RsaKeys
import net.matsudamper.kotpub.delivery.DeliveryResult
import net.matsudamper.kotpub.httpsignature.HttpSignatureSigner

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class FederationRoutesTest {
    private val repositories = FakeRepositories()
    private val keyPair = RsaKeys.generateKeyPair()
    private val remoteActors = FakeRemoteActors()
    private val activityDelivery = FakeActivityDelivery()
    private val deps = TestDependencies(
        repositories = repositories,
        env = ServerEnv(mapOf("DOMAIN" to "example.com")),
        remoteActors = remoteActors,
        activityDelivery = activityDelivery,
    )
    private val remoteKeyPair = RsaKeys.generateKeyPair()

    @Test
    fun `WebFinger で Actor の URL を返す`() =
        testApplication {
            setUp()

            val response = client.get("/.well-known/webfinger?resource=acct:User1@Example.com")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType("application", "jrd+json"), response.contentType()?.withoutParameters())
            val body = parse(response.bodyAsText())
            assertEquals("acct:user1@example.com", body.getValue("subject").jsonPrimitive.content)
            val selfLink = body.getValue("links").jsonArray.map { it.jsonObject }.single { it.getValue("rel").jsonPrimitive.content == "self" }
            assertEquals("application/activity+json", selfLink.getValue("type").jsonPrimitive.content)
            assertEquals("https://example.com/users/user1", selfLink.getValue("href").jsonPrimitive.content)
        }

    @Test
    fun `WebFinger は他のドメインといない名前に 404 を返す`() =
        testApplication {
            setUp()

            assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/webfinger?resource=acct:user1@other.example").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/webfinger?resource=acct:user2@example.com").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/.well-known/webfinger").status)
        }

    @Test
    fun `Actor に公開鍵を載せる`() =
        testApplication {
            setUp()

            val response = client.get("/users/user1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType("application", "activity+json"), response.contentType()?.withoutParameters())
            val body = parse(response.bodyAsText())
            assertEquals("https://example.com/users/user1", body.getValue("id").jsonPrimitive.content)
            assertEquals("Service", body.getValue("type").jsonPrimitive.content)
            assertEquals("user1", body.getValue("preferredUsername").jsonPrimitive.content)
            assertEquals("https://example.com/users/user1/inbox", body.getValue("inbox").jsonPrimitive.content)
            val publicKey = body.getValue("publicKey").jsonObject
            assertEquals("https://example.com/users/user1#main-key", publicKey.getValue("id").jsonPrimitive.content)
            assertEquals("https://example.com/users/user1", publicKey.getValue("owner").jsonPrimitive.content)
            assertEquals(RsaKeys.encodeToPem(keyPair.public), publicKey.getValue("publicKeyPem").jsonPrimitive.content)
        }

    @Test
    fun `鍵の無いアカウントの Actor は 404 を返す`() =
        testApplication {
            repositories.accountRepository.add(username = "nokey", passwordHash = "hash", passwordHashType = "type")
            setUp()

            assertEquals(HttpStatusCode.NotFound, client.get("/users/nokey").status)
        }

    @Test
    fun `DOMAIN が無いときは WebFinger を返さない`() =
        testApplication {
            application {
                module(TestDependencies(repositories = repositories))
            }

            assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/webfinger?resource=acct:user1@example.com").status)
        }

    @Test
    fun `Follow に Accept を送るとフォロワーに数える`() =
        testApplication {
            setUp()
            remoteActors.add(REMOTE_ACTOR.copy(publicKeyPem = RsaKeys.encodeToPem(remoteKeyPair.public)))

            val response = postSigned("/users/user1/inbox", followJson())

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals(0, followerCount())

            deps.followAcceptSender("example.com").sendDue()

            val accept = parse(activityDelivery.delivered.single().body)
            assertEquals("https://remote.example/users/alice/inbox", activityDelivery.delivered.single().inbox)
            assertEquals("Accept", accept.getValue("type").jsonPrimitive.content)
            assertEquals("https://example.com/users/user1", accept.getValue("actor").jsonPrimitive.content)
            assertEquals(FOLLOW_ID, accept.getValue("object").jsonObject.getValue("id").jsonPrimitive.content)
            assertEquals(1, followerCount())
        }

    @Test
    fun `Accept を送れなければフォロワーに数えない`() =
        testApplication {
            setUp()
            remoteActors.add(REMOTE_ACTOR.copy(publicKeyPem = RsaKeys.encodeToPem(remoteKeyPair.public)))
            activityDelivery.nextResult = DeliveryResult.Failed(reason = "test", retryable = true)

            postSigned("/users/user1/inbox", followJson())
            deps.followAcceptSender("example.com").sendDue()

            assertEquals(0, followerCount())
        }

    @Test
    fun `Undo でフォローを解除する`() =
        testApplication {
            setUp()
            remoteActors.add(REMOTE_ACTOR.copy(publicKeyPem = RsaKeys.encodeToPem(remoteKeyPair.public)))
            postSigned("/users/user1/inbox", followJson())
            deps.followAcceptSender("example.com").sendDue()

            val undo =
                buildJsonObject {
                    put("@context", "https://www.w3.org/ns/activitystreams")
                    put("id", "https://remote.example/users/alice#follows/1/undo")
                    put("type", "Undo")
                    put("actor", REMOTE_ACTOR.actorId)
                    put("object", Json.parseToJsonElement(followJson()))
                }.toString()
            val response = postSigned("/inbox", undo)

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals(0, followerCount())
        }

    @Test
    fun `署名の無い Follow は受け付けない`() =
        testApplication {
            setUp()

            val response =
                client.post("/users/user1/inbox") {
                    contentType(ContentType("application", "activity+json"))
                    setBody(followJson())
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    private fun ApplicationTestBuilder.setUp() {
        val id = repositories.accountRepository.add(username = "user1", passwordHash = "hash", passwordHashType = "type")
        repositories.accountRepository.addPublicKeyPem(id = id, publicKeyPem = RsaKeys.encodeToPem(keyPair.public))
        repositories.accountRepository.addPrivateKeyPem(id = id, privateKeyPem = RsaKeys.encodeToPem(keyPair.private))
        application {
            module(deps)
        }
    }

    private suspend fun ApplicationTestBuilder.postSigned(
        path: String,
        body: String,
    ): HttpResponse {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val signedHeaders =
            HttpSignatureSigner().sign(
                keyId = "${REMOTE_ACTOR.actorId}#main-key",
                privateKey = remoteKeyPair.private,
                method = "POST",
                requestTarget = path,
                host = "localhost",
                body = bodyBytes,
            )
        return client.post(path) {
            signedHeaders.forEach { (name, value) -> header(name, value) }
            contentType(ContentType("application", "activity+json"))
            setBody(bodyBytes)
        }
    }

    private suspend fun ApplicationTestBuilder.followerCount(): Long =
        parse(client.get("/users/user1/followers").bodyAsText()).getValue("totalItems").jsonPrimitive.long

    private fun followJson(): String =
        buildJsonObject {
            put("@context", "https://www.w3.org/ns/activitystreams")
            put("id", FOLLOW_ID)
            put("type", "Follow")
            put("actor", REMOTE_ACTOR.actorId)
            put("object", "https://example.com/users/user1")
        }.toString()

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private companion object {
        const val FOLLOW_ID = "https://remote.example/users/alice#follows/1"

        val REMOTE_ACTOR =
            RemoteActor(
                actorId = "https://remote.example/users/alice",
                inbox = "https://remote.example/users/alice/inbox",
                sharedInbox = "https://remote.example/inbox",
                publicKeyPem = "",
                profile = RemoteActorProfile(preferredUsername = "alice", displayName = null, profileUrl = null, iconUrl = null),
            )
    }
}
