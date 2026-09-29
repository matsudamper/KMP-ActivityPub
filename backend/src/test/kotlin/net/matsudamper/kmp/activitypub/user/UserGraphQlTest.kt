package net.matsudamper.kmp.activitypub.user

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
import net.matsudamper.activitypub.json.AppJson
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.TestServerEnv
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.repository.IncomingFollow
import net.matsudamper.kmp.activitypub.repository.NewRemoteActor
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfile
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH
import net.matsudamper.kmp.activitypub.testDependencies

// ユーザーのログインと投稿を GraphQL の口から確認する。
// Cookie の保存はクライアント側の実装に寄るので、テストでは Set-Cookie を自分で読んで付け直す。
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
            assertEquals("@$USERNAME@${TestServerEnv.DOMAIN}", result.obj("session").obj("account").string("acct"))
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
    fun `ログアウトすると同じ Cookie では通らなくなる`() =
        testApplication {
            applicationWith(repositoriesWithUser())
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            graphQl("mutation { user { logout { account { username } } } }", token = token)

            assertEquals(JsonNull, querySession(token).user().obj("session").getValue("account"))
        }

    @Test
    fun `管理画面の Cookie ではユーザーとしてログインしたことにならない`() =
        testApplication {
            applicationWith(repositoriesWithUser())
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            val response = client.post(GRAPHQL_PATH) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Cookie, "${SessionCookieManager.ADMIN_COOKIE_NAME}=$token")
                setBody("""{"query":"query { user { session { account { username } } } }"}""")
            }

            assertEquals(JsonNull, response.user().obj("session").getValue("account"))
        }

    @Test
    fun `投稿するとフォロワーの inbox ぶんが配信キューに入る`() =
        testApplication {
            val repositories = repositoriesWithUser()
            repositories.addFollower()
            applicationWith(repositories)
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            val result = mutatePostNote("こんにちは", token).user().obj("postNote")

            assertEquals(JsonNull, result.getValue("failure"))
            assertContains(result.obj("note").string("url"), "https://${TestServerEnv.DOMAIN}/")
            assertEquals(1L, repositories.notes.count(USERNAME))
            assertEquals(listOf(FOLLOWER_INBOX), repositories.deliveryQueue.rows().map { it.inbox })
        }

    @Test
    fun `空白だけの本文は投稿しない`() =
        testApplication {
            val repositories = repositoriesWithUser()
            applicationWith(repositories)
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            val failure = mutatePostNote("  \n ", token).user().obj("postNote").obj("failure")

            assertTrue(failure.boolean("isEmpty"))
            assertEquals(0L, repositories.notes.count(USERNAME))
        }

    @Test
    fun `上限を超える本文は上限と一緒に拒否される`() =
        testApplication {
            val repositories = repositoriesWithUser()
            applicationWith(repositories)
            val token = assertNotNull(mutateLogin(USERNAME, PASSWORD).sessionCookieValue())

            val failure = mutatePostNote("あ".repeat(501), token).user().obj("postNote").obj("failure")

            assertEquals(500, failure.getValue("maxLength").jsonPrimitive.int)
            assertEquals(0L, repositories.notes.count(USERNAME))
        }

    @Test
    fun `ログインしていなければ投稿できない`() =
        testApplication {
            val repositories = repositoriesWithUser()
            applicationWith(repositories)

            assertTrue(mutatePostNote("こんにちは").body().containsKey("errors"))
            assertEquals(0L, repositories.notes.count(USERNAME))
        }

    private fun repositoriesWithUser(): FakeRepositories =
        FakeRepositories().apply {
            accounts.add(username = USERNAME, passwordHash = PASSWORD_HASH, createdAt = Instant.now())
        }

    private fun FakeRepositories.addFollower() {
        followers.record(
            IncomingFollow(
                username = USERNAME,
                follower = NewRemoteActor(
                    actorUri = FOLLOWER_ACTOR_URI,
                    inbox = FOLLOWER_INBOX,
                    sharedInbox = null,
                    publicKeyPem = "pem",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "$FOLLOWER_ACTOR_URI#follow",
                receivedAt = Instant.now(),
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        followers.markAccepted(USERNAME, FOLLOWER_ACTOR_URI)
        // Accept の行が残っていると、投稿の配信だけを数えられない
        deliveryQueue.deleteByUsername(USERNAME)
    }

    private fun ApplicationTestBuilder.applicationWith(repositories: FakeRepositories) {
        application {
            module(testDependencies(repositories = repositories))
        }
    }

    private suspend fun ApplicationTestBuilder.querySession(token: String? = null): HttpResponse =
        graphQl("query { user { session { account { username acct } } } }", token = token)

    private suspend fun ApplicationTestBuilder.mutateLogin(
        username: String,
        password: String,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Login(${'$'}query: UserLoginQuery!) { user { " +
                "login(query: ${'$'}query) { session { account { username acct } } failure } } }",
            variables = """{"query":{"username":${JsonPrimitive(username)},"password":${JsonPrimitive(password)}}}""",
        )

    private suspend fun ApplicationTestBuilder.mutatePostNote(
        body: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Post(${'$'}body: String!) { user { " +
                "postNote(body: ${'$'}body) { note { id url } failure { isEmpty maxLength } } } }",
            token = token,
            variables = """{"body":${JsonPrimitive(body)}}""",
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

        const val FOLLOWER_ACTOR_URI = "https://remote.example/users/alice"

        const val FOLLOWER_INBOX = "https://remote.example/users/alice/inbox"

        /**
         * 反復回数は検証にも使われるので、落としても経路は同じ。既定だとテストのたびに待つ
         */
        val PASSWORD_HASH: String = PasswordHash.create(PASSWORD, iterations = 1_000).encode()

        suspend fun HttpResponse.body(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

        /**
         * `data.user` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.user(): JsonObject = body().obj("data").obj("user")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == SessionCookieManager.USER_COOKIE_NAME }?.value

        fun HttpRequestBuilder.withSessionCookie(token: String) {
            header(HttpHeaders.Cookie, "${SessionCookieManager.USER_COOKIE_NAME}=$token")
        }
    }
}
