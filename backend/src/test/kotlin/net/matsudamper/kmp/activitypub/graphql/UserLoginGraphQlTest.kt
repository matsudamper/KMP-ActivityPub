package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import net.matsudamper.kmp.activitypub.TestDependencies
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.session.SessionCookieManager

class UserLoginGraphQlTest {
    @Test
    fun `正しいパスワードでログインするとセッション Cookie が返る`() =
        testApplication {
            applicationWithUser()

            val response = login(username = USERNAME, password = PASSWORD)

            assertEquals(JsonNull, response.login().getValue("failure"))
            val setCookie = assertNotNull(response.headers[HttpHeaders.SetCookie])
            assertContains(setCookie, "HttpOnly")
            assertContains(setCookie, "Secure")
        }

    @Test
    fun `パスワードが違えばログインできず Cookie も返らない`() =
        testApplication {
            applicationWithUser()

            val response = login(username = USERNAME, password = "wrong-password")

            assertEquals("WRONG_USERNAME_OR_PASSWORD", response.login().string("failure"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `いないユーザーはパスワードが違うのと同じ結果になる`() =
        testApplication {
            applicationWithUser()

            val response = login(username = "unknown", password = PASSWORD)

            assertEquals("WRONG_USERNAME_OR_PASSWORD", response.login().string("failure"))
        }

    @Test
    fun `1 つのリクエストで login を 2 回呼ぶと 2 回目は失敗する`() =
        testApplication {
            applicationWithUser()

            val response =
                graphQl(
                    query =
                    "mutation Login(${'$'}query: UserLoginQuery!) { user { " +
                        "first: login(query: ${'$'}query) { failure } " +
                        "second: login(query: ${'$'}query) { failure } } }",
                    variables = loginVariables(username = USERNAME, password = PASSWORD),
                )

            assertTrue(response.body().containsKey("errors"))
        }

    private fun ApplicationTestBuilder.applicationWithUser() {
        val repositories =
            FakeRepositories().apply {
                accountRepository.add(
                    username = USERNAME,
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PasswordHashType.Current.dbValue,
                )
            }
        application {
            module(TestDependencies(repositories = repositories))
        }
    }

    private suspend fun ApplicationTestBuilder.login(
        username: String,
        password: String,
    ): HttpResponse =
        graphQl(
            query = "mutation Login(${'$'}query: UserLoginQuery!) { user { login(query: ${'$'}query) { failure } } }",
            variables = loginVariables(username = username, password = password),
        )

    private suspend fun ApplicationTestBuilder.graphQl(
        query: String,
        variables: String,
    ): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"query":${JsonPrimitive(query)},"variables":$variables}""")
        }

    private companion object {
        const val USERNAME = "user1"

        const val PASSWORD = "user-password"

        val PASSWORD_HASH: String = checkNotNull(PasswordHashType.Current.encoder.encode(PASSWORD))

        fun loginVariables(
            username: String,
            password: String,
        ): String = """{"query":{"username":${JsonPrimitive(username)},"password":${JsonPrimitive(password)}}}"""

        suspend fun HttpResponse.body(): JsonObject = ServerJson.value.parseToJsonElement(bodyAsText()).jsonObject

        suspend fun HttpResponse.login(): JsonObject = body().obj("data").obj("user").obj("login")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == SessionCookieManager.USER_COOKIE_NAME }?.value
    }
}
