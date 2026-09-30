package net.matsudamper.kmp.activitypub.admin

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.activitypub.json.AppJson
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.TestServerEnv
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH
import net.matsudamper.kmp.activitypub.testDependencies

// 管理画面のログインを GraphQL の口から確認する。
// Cookie の保存はクライアント側の実装に寄るので、テストでは Set-Cookie を自分で読んで付け直す。
class AdminGraphQlTest {
    @Test
    fun `ログインしていなければ loggedIn は false`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = querySession()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val session = response.session()
            assertFalse(session.boolean("loggedIn"))
            assertTrue(session.boolean("passwordConfigured"))
        }

    @Test
    fun `パスワードハッシュが未設定なら passwordConfigured は false`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            assertFalse(querySession().session().boolean("passwordConfigured"))
        }

    @Test
    fun `パスワードハッシュが未設定ならログインできない`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            val response = mutateLogin(PASSWORD)

            val result = response.loginResult()
            assertEquals("NOT_CONFIGURED", result.string("failure"))
            assertFalse(result.obj("session").boolean("loggedIn"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `正しいパスワードでログインするとセッション Cookie が返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = mutateLogin(PASSWORD)

            assertEquals(HttpStatusCode.OK, response.status)

            val result = response.loginResult()
            assertTrue(result.obj("session").boolean("loggedIn"))
            assertEquals(JsonNull, result.getValue("failure"))
            assertNotNull(response.sessionCookieValue())
        }

    @Test
    fun `セッション Cookie は HttpOnly と SameSite と Secure が付く`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val setCookie = assertNotNull(mutateLogin(PASSWORD).headers[HttpHeaders.SetCookie])

            assertContains(setCookie, "HttpOnly")
            assertContains(setCookie, "SameSite=Strict")
            assertContains(setCookie, "Secure")
            assertContains(setCookie, "Path=/")
        }

    @Test
    fun `COOKIE_SECURE が false なら Secure を付けない`() =
        testApplication {
            applicationWith(passwordConfigured = true, cookieSecure = false)

            val setCookie = assertNotNull(mutateLogin(PASSWORD).headers[HttpHeaders.SetCookie])

            assertFalse(setCookie.contains("Secure"))
            assertContains(setCookie, "HttpOnly")
        }

    @Test
    fun `パスワードが違えば WRONG_PASSWORD で Cookie も返らない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = mutateLogin("ちがうパスワード")

            val result = response.loginResult()
            assertEquals("WRONG_PASSWORD", result.string("failure"))
            assertFalse(result.obj("session").boolean("loggedIn"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `ログインで得た Cookie を付ければログイン済みになる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertTrue(querySession(token).session().boolean("loggedIn"))
        }

    @Test
    fun `知らない Cookie ではログイン済みにならない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            assertFalse(querySession("知らないトークン").session().boolean("loggedIn"))
        }

    @Test
    fun `ログアウトすると同じ Cookie では通らなくなる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertFalse(mutateLogout(token).admin().obj("logout").boolean("loggedIn"))

            // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
            assertFalse(querySession(token).session().boolean("loggedIn"))
        }

    @Test
    fun `ログアウトは Cookie を消す指示を返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertEquals("", mutateLogout(token).sessionCookieValue())
        }

    @Test
    fun `本文が GraphQL のリクエストとして読めなければ 400`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("パスワード")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `ログインしていなければユーザーを列挙できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            assertTrue(queryAccounts().body().containsKey("errors"))
        }

    @Test
    fun `ユーザーがいなければ列挙は空`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertEquals(listOf(), queryAccounts(token).accounts().nodes())
        }

    @Test
    fun `登録したユーザーが列挙に入る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val added = assertNotNull(mutateAddUser("user1", USER_PASSWORD, token).addUserResult().obj("adminAccount"))

            assertEquals("user1", added.string("username"))
            assertEquals("@user1@${TestServerEnv.DOMAIN}", added.string("acct"))
            // 時刻は文字列にせずエポックからの秒数で返す。書式の解釈を受け取る側に委ねない
            assertTrue(added.getValue("createdAt").jsonPrimitive.long > 0)
            assertNotNull(added.getValue("id").jsonPrimitive.long)

            assertEquals(
                listOf("user1"),
                queryAccounts(token).accounts().nodes().map { it.string("username") },
            )
        }

    @Test
    fun `登録したユーザーのパスワードはハッシュで持つ`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(passwordConfigured = true, repositories = repositories)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            mutateAddUser("user1", USER_PASSWORD, token)

            val credential = assertNotNull(repositories.accounts.findCredential("user1"))
            assertNotEquals(USER_PASSWORD, credential.passwordHash)
            assertTrue(PasswordHash.parse(credential.passwordHash).matches(USER_PASSWORD))
        }

    @Test
    fun `1 件ずつでも登録した順に辿れる`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "user1", passwordHash = "hash", createdAt = Instant.now())
            repositories.accounts.add(username = "user2", passwordHash = "hash", createdAt = Instant.now())
            applicationWith(passwordConfigured = true, repositories = repositories)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val page1 = queryAccounts(token, limit = 1).accounts()
            assertEquals(listOf("user1"), page1.nodes().map { it.string("username") })
            assertEquals(true, page1.pageInfo().boolean("hasMore"))

            val page2 = queryAccounts(token, cursor = page1.pageInfo().string("nextCursor"), limit = 1).accounts()
            assertEquals(listOf("user2"), page2.nodes().map { it.string("username") })
            assertEquals(false, page2.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `limit が上限を超えていても 50 件までしか返さない`() =
        testApplication {
            val repositories = FakeRepositories()
            repeat(51) { repositories.accounts.add(username = "user$it", passwordHash = "hash", createdAt = Instant.now()) }
            applicationWith(passwordConfigured = true, repositories = repositories)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val page = queryAccounts(token, limit = Int.MAX_VALUE).accounts()

            assertEquals(50, page.nodes().size)
            assertEquals(true, page.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `使えない文字は入力にあったものを返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val result = mutateAddUser("user 1/あ", USER_PASSWORD, token).addUserResult()

            assertEquals(JsonNull, result.getValue("adminAccount"))
            // どの文字が駄目なのかを画面が自分で決めなくて済むようにする
            assertEquals(
                listOf(" ", "/", "あ"),
                result.failure().getValue("unusableCharacters").jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun `短すぎるパスワードは下限と一緒に返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val result = mutateAddUser("user1", "short", token).addUserResult()

            assertEquals(JsonNull, result.getValue("adminAccount"))
            assertEquals(8, result.failure().int("passwordMinLength"))
        }

    @Test
    fun `同じ名前は登録できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddUser("user1", USER_PASSWORD, token)

            val result = mutateAddUser("USER1", USER_PASSWORD, token).addUserResult()

            assertTrue(result.failure().boolean("isDuplicated"))
        }

    @Test
    fun `ログインしていなければユーザーを登録できない`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(passwordConfigured = true, repositories = repositories)

            assertTrue(mutateAddUser("user1", USER_PASSWORD).body().containsKey("errors"))
            assertNull(repositories.accounts.findByUsername("user1"))
        }

    // スキーマに無いものが通ってしまうと、結線の漏れに気付けない
    @Test
    fun `スキーマに無いフィールドは errors になる`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = graphQl("query { admin { 知らないフィールド } }")

            // 問い合わせのエラーは HTTP 200 で errors に入る
            assertEquals(HttpStatusCode.OK, response.status)
            val errors = response.body().getValue("errors").jsonArray
            assertEquals(1, errors.size)
            assertEquals(
                GraphQlEngine.GENERIC_ERROR_MESSAGE,
                errors.single().jsonObject.getValue("message").jsonPrimitive.content,
            )
            assertEquals(setOf("message"), errors.single().jsonObject.keys)
        }

    private fun ApplicationTestBuilder.applicationWith(
        passwordConfigured: Boolean,
        cookieSecure: Boolean = true,
        repositories: FakeRepositories = FakeRepositories(),
    ) {
        val values =
            buildList {
                add("COOKIE_SECURE" to cookieSecure.toString())
                if (passwordConfigured) add("ADMIN_PASSWORD_HASH" to PASSWORD_HASH)
            }
        application {
            module(
                testDependencies(
                    repositories = repositories,
                    env = TestServerEnv.of(*values.toTypedArray()),
                ),
            )
        }
    }

    private suspend fun ApplicationTestBuilder.querySession(token: String? = null): HttpResponse =
        graphQl("query { admin { session { loggedIn passwordConfigured } } }", token = token)

    private suspend fun ApplicationTestBuilder.mutateLogin(password: String): HttpResponse =
        graphQl(
            query =
            "mutation Login(${'$'}password: String!) { admin { " +
                "login(password: ${'$'}password) { session { loggedIn passwordConfigured } failure } } }",
            variables = """{"password":${JsonPrimitive(password)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutateLogout(token: String): HttpResponse =
        graphQl("mutation { admin { logout { loggedIn passwordConfigured } } }", token = token)

    private suspend fun ApplicationTestBuilder.queryAccounts(
        token: String? = null,
        cursor: String? = null,
        limit: Int? = null,
    ): HttpResponse =
        graphQl(
            query =
            "query Accounts(${'$'}cursor: String, ${'$'}limit: Int) { admin { " +
                "adminAccounts(cursor: ${'$'}cursor, limit: ${'$'}limit) { " +
                "nodes { $ACCOUNT_FIELDS } pageInfo { hasMore nextCursor } } } }",
            token = token,
            variables = buildString {
                append("{")
                if (cursor != null) append(""""cursor":${JsonPrimitive(cursor)},""")
                append(""""limit":${if (limit == null) "null" else JsonPrimitive(limit)}""")
                append("}")
            },
        )

    private suspend fun ApplicationTestBuilder.mutateAddUser(
        username: String,
        password: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Add(${'$'}query: AddUserQuery!) { admin { " +
                "addUser(query: ${'$'}query) { adminAccount { $ACCOUNT_FIELDS } " +
                "failure { unusableCharacters maxLength minLength isDuplicated passwordMinLength } } } }",
            token = token,
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
        const val PASSWORD = "とても長いパスワード"

        const val USER_PASSWORD = "user-password"

        const val ACCOUNT_FIELDS = "id username acct createdAt"

        /**
         * 反復回数は検証にも使われるので、落としても経路は同じ。既定だとテストのたびに待つ
         */
        val PASSWORD_HASH: String = PasswordHash.create(PASSWORD, iterations = 1_000).encode()

        suspend fun HttpResponse.body(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

        /**
         * `data.admin` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.admin(): JsonObject = body().obj("data").obj("admin")

        suspend fun HttpResponse.session(): JsonObject = admin().obj("session")

        suspend fun HttpResponse.loginResult(): JsonObject = admin().obj("login")

        suspend fun HttpResponse.accounts(): JsonObject = admin().obj("adminAccounts")

        fun JsonObject.nodes(): List<JsonObject> = getValue("nodes").jsonArray.map { it.jsonObject }

        fun JsonObject.pageInfo(): JsonObject = obj("pageInfo")

        suspend fun HttpResponse.addUserResult(): JsonObject = admin().obj("addUser")

        fun JsonObject.failure(): JsonObject = obj("failure")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

        /**
         * Set-Cookie のセッション。無ければ null
         */
        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == SessionCookieManager.ADMIN_COOKIE_NAME }?.value

        fun HttpRequestBuilder.withSessionCookie(token: String) {
            header(HttpHeaders.Cookie, "${SessionCookieManager.ADMIN_COOKIE_NAME}=$token")
        }
    }
}
