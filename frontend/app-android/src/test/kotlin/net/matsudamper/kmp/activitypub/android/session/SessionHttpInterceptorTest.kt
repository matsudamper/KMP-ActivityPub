package net.matsudamper.kmp.activitypub.android.session

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptorChain
import okhttp3.HttpUrl.Companion.toHttpUrl

class SessionHttpInterceptorTest {
    @Test
    fun `Cookieヘッダーを付けてセッションCookieだけ保存する`() = runTest {
        val local = MemoryStore()
        val repository = SessionRepository(local, NoBackup, backgroundScope, reportBackupFailure = {})
        val interceptor = SessionHttpInterceptor(repository)
        val request = HttpRequest.Builder(HttpMethod.Post, "https://example.com/graphql")
            .addHeader("cookie", "unrelated=secret")
            .addHeader("Content-Type", "application/json")
            .build()
        interceptor.intercept(
            request,
            object : HttpInterceptorChain {
                override suspend fun proceed(request: HttpRequest): HttpResponse {
                    assertEquals(listOf("user_session=previous"), request.headers.filter { it.name.equals("Cookie", true) }.map { it.value })
                    assertEquals("application/json", request.headers.first { it.name == "Content-Type" }.value)
                    return HttpResponse.Builder(200)
                        .addHeader("set-cookie", "user_session=next; Path=/; HttpOnly; Secure")
                        .addHeader("Set-Cookie", "unrelated=other; Path=/")
                        .build()
                }
            },
        )
        assertEquals("next", local.state.session?.forRequest("https://example.com/graphql".toHttpUrl(), 0)?.value)
    }

    @Test
    fun `認証失効時はセッションを削除する`() = runTest {
        val local = MemoryStore()
        val repository = SessionRepository(local, NoBackup, backgroundScope, reportBackupFailure = {})
        SessionHttpInterceptor(repository).intercept(
            HttpRequest.Builder(HttpMethod.Post, "https://example.com/graphql").build(),
            object : HttpInterceptorChain {
                override suspend fun proceed(request: HttpRequest): HttpResponse = HttpResponse.Builder(401).build()
            },
        )
        assertNull(local.state.session)
    }

    @Test
    fun `HTTPへの送信は通信前に拒否する`() = runTest {
        val repository = SessionRepository(MemoryStore(), NoBackup, backgroundScope, reportBackupFailure = {})
        assertFailsWith<IOException> {
            SessionHttpInterceptor(repository).intercept(
                HttpRequest.Builder(HttpMethod.Post, "http://example.com/graphql").build(),
                object : HttpInterceptorChain {
                    override suspend fun proceed(request: HttpRequest): HttpResponse = error("通信してはいけない")
                },
            )
        }
    }

    private class MemoryStore : SessionLocalStore {
        var state = SessionState(SessionCookie("https://example.com/", "user_session=previous; Path=/"), false)

        override suspend fun read(): SessionState = state

        override suspend fun write(state: SessionState) {
            this.state = state
        }
    }

    private object NoBackup : SessionBackupStore {
        override suspend fun restore(): SessionCookie? = null

        override suspend fun save(session: SessionCookie?) = Unit
    }
}
