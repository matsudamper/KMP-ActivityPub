package net.matsudamper.kmp.activitypub.federation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.ServerEnv
import net.matsudamper.kmp.activitypub.TestDependencies
import net.matsudamper.kmp.activitypub.TestEnv
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kotpub.crypto.RsaKeys

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class FederationRoutesTest {
    private val repositories = FakeRepositories()
    private val keyPair = RsaKeys.generateKeyPair()

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
    fun `プロフィールページの URL でも ActivityPub の取得なら Actor を返す`() =
        testApplication {
            setUp()

            val response =
                client.get("/@user1") {
                    header(HttpHeaders.Accept, "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType("application", "ld+json"), response.contentType()?.withoutParameters())
            assertEquals("https://example.com/users/user1", parse(response.bodyAsText()).getValue("id").jsonPrimitive.content)
        }

    @Test
    fun `プロフィールページの URL をブラウザで開いたときは Actor を返さない`() =
        testApplication {
            setUp()

            val response =
                client.get("/@user1") {
                    header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }

            assertNotEquals(ContentType("application", "activity+json"), response.contentType()?.withoutParameters())
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

    private fun ApplicationTestBuilder.setUp() {
        val id = repositories.accountRepository.add(username = "user1", passwordHash = "hash", passwordHashType = "type")
        repositories.accountRepository.addPublicKeyPem(id = id, publicKeyPem = RsaKeys.encodeToPem(keyPair.public))
        application {
            module(TestDependencies(repositories = repositories, env = ServerEnv(TestEnv.REQUIRED + mapOf("DOMAIN" to "example.com"))))
        }
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
