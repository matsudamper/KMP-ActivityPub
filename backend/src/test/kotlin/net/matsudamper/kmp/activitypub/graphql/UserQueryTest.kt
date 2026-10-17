package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.TestDependencies
import net.matsudamper.kmp.activitypub.module

class UserQueryTest {
    @Test
    fun `ログインしていなくてもユーザー名でユーザーを引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accountRepository.add(username = "alice", passwordHash = "hash", passwordHashType = "type")
            application { module(TestDependencies(repositories = repositories)) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"query { user(username: \"Alice\") { username } }"}""")
                }

            assertEquals(json("""{"data":{"user":{"username":"alice"}}}"""), json(response.bodyAsText()))
        }

    @Test
    fun `いないユーザーは null`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"query { user(username: \"alice\") { username } }"}""")
                }

            assertEquals(json("""{"data":{"user":null}}"""), json(response.bodyAsText()))
        }

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)
}
