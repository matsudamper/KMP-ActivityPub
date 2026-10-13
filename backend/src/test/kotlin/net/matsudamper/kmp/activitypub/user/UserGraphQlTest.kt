package net.matsudamper.kmp.activitypub.user

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH
import net.matsudamper.kmp.activitypub.testDependencies

// Set-Cookie はテストで自分で読んで付け直す
class UserGraphQlTest {
    @Test
    fun `ログインしていなければ account は null`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            assertEquals(JsonNull, querySession().user().obj("session").getValue("account"))
        }

    @Test
    fun `正しいパスワードでログインするとセッション Cookie が返る`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            val response = mutateLogin(USERNAME, PASSWORD)

            val result = response.user().obj("login")
            assertEquals(JsonNull, result.getValue("failure"))
            assertEquals(USERNAME, result.obj("session").obj("account").string("username"))
            val setCookie = assertNotNull(response.headers[HttpHeaders.SetCookie])
            assertContains(setCookie, "HttpOnly")
            assertContains(setCookie, "Secure")
        }

    @Test
    fun `パスワードが違えばログインできず Cookie も返らない`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            val response = mutateLogin(USERNAME, "wrong-password")

            assertEquals("WRONG_USERNAME_OR_PASSWORD", response.user().obj("login").string("failure"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `いないユーザーはパスワードが違うのと同じ結果になる`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            val response = mutateLogin("unknown", PASSWORD)

            assertEquals("WRONG_USERNAME_OR_PASSWORD", response.user().obj("login").string("failure"))
        }

    @Test
    fun `ログインで得た Cookie を付ければログイン済みになる`() =
        testApplication {
            applicationWith(repositoriesWithUser())
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            val account = querySession(token).user().obj("session").obj("account")

            assertEquals(USERNAME, account.string("username"))
        }

    @Test
    fun `1 つのリクエストで login を 2 回呼ぶと 2 回目は失敗する`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            val response = graphQl(
                query =
                "mutation Login(${'$'}query: UserLoginQuery!) { user { " +
                    "first: login(query: ${'$'}query) { failure } " +
                    "second: login(query: ${'$'}query) { failure } } }",
                variables = """{"query":{"username":${JsonPrimitive(USERNAME)},"password":${JsonPrimitive(PASSWORD)}}}""",
            )

            assertTrue(response.body().containsKey("errors"))
        }

    @Test
    fun `alias で session を大量に並べた問い合わせは実行しない`() =
        testApplication {
            applicationWith(repositoriesWithUser())

            val aliases = (1..200).joinToString(" ") { "s$it: session { account { username } }" }
            val response = graphQl(query = "query { user { $aliases } }")

            assertTrue(response.body().containsKey("errors"))
        }

    private fun repositoriesWithUser(): FakeRepositories =
        FakeRepositories().apply {
            accounts.add(username = USERNAME, passwordHash = PASSWORD_HASH)
        }

    private fun ApplicationTestBuilder.applicationWith(repositories: FakeRepositories) {
        application {
            module(testDependencies(repositories = repositories))
        }
    }

    private suspend fun ApplicationTestBuilder.querySession(token: String? = null): HttpResponse =
        graphQl("query { user { session { account { username } } } }", token = token)

    private suspend fun ApplicationTestBuilder.mutateLogin(
        username: String,
        password: String,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Login(${'$'}query: UserLoginQuery!) { user { " +
                "login(query: ${'$'}query) { session { account { username } } failure } } }",
            variables = """{"query":{"username":${JsonPrimitive(username)},"password":${JsonPrimitive(password)}}}""",
        )

    private suspend fun ApplicationTestBuilder.graphQl(
        query: String,
        token: String? = null,
        variables: String? = null,
    ): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)
            if (token != null) withSessionCookie(token)

            setBody(
                buildString {
                    append("""{"query":${JsonPrimitive(query)}""")
                    if (variables != null) append(""","variables":$variables""")
                    append("}")
                },
            )
        }

    private companion object {
        const val USERNAME = "user1"

        const val PASSWORD = "user-password"

        val PASSWORD_HASH: String = PasswordHashType.Current.encoder.encode(PASSWORD)!!

        suspend fun HttpResponse.body(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

        suspend fun HttpResponse.user(): JsonObject = body().obj("data").obj("user")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == SessionCookieManager.USER_COOKIE_NAME }?.value

        fun HttpRequestBuilder.withSessionCookie(token: String) {
            header(HttpHeaders.Cookie, "${SessionCookieManager.USER_COOKIE_NAME}=$token")
        }
    }
}
