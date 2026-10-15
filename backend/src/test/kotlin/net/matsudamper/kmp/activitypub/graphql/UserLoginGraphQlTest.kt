package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.module

class UserLoginGraphQlTest {
    @Test
    fun `login はどの入力でも成功し期限の無いダミーCookieを返す`() =
        testApplication {
            application {
                module(staticFiles = null)
            }

            assertDummySession(login(username = "someone", password = "secret"))
            assertDummySession(login(username = "someone", password = "other"))
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.login(
        username: String,
        password: String,
    ): io.ktor.client.statement.HttpResponse {
        val response =
            client.post(GRAPHQL_PATH) {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"query":"mutation(${'$'}query: UserLoginQuery!) { user { login(query: ${'$'}query) { failure } } }","variables":{"query":{"username":"$username","password":"$password"}}}
                    """.trimIndent(),
                )
            }

        assertEquals(HttpStatusCode.OK, response.status)
        return response
    }

    private suspend fun assertDummySession(response: io.ktor.client.statement.HttpResponse) {
        val body = ServerJson.value.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNull(body["errors"])
        assertEquals(JsonNull, body.obj("data").obj("user").obj("login")["failure"])

        val header = response.headers[HttpHeaders.SetCookie]
        val cookie = parseServerSetCookieHeader(header ?: "")
        assertEquals(GraphQlContext.USER_SESSION_COOKIE_NAME, cookie.name)
        assertEquals(GraphQlContext.DUMMY_USER_SESSION, cookie.value)
        assertNull(cookie.maxAge)
        assertNull(cookie.expires)
        assertEquals("/", cookie.path)
        assertEquals(true, cookie.httpOnly)
    }

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
}
