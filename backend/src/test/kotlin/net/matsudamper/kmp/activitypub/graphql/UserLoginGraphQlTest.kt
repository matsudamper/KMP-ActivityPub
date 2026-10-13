package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

class UserLoginGraphQlTest {
    @Test
    fun `login はどの入力でも失敗を返す`() =
        testApplication {
            application {
                module(staticFiles = null)
            }

            assertEquals("WRONG_USERNAME_OR_PASSWORD", login(username = "someone", password = "secret"))
            assertEquals("WRONG_USERNAME_OR_PASSWORD", login(username = "someone", password = "other"))
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.login(
        username: String,
        password: String,
    ): String {
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
        val body = ServerJson.value.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNull(body["errors"])
        return body.obj("data").obj("user").obj("login").string("failure")
    }

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
