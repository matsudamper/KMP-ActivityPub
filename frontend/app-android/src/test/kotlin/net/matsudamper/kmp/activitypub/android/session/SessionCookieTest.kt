package net.matsudamper.kmp.activitypub.android.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class SessionCookieTest {
    private val url = "https://example.com/graphql".toHttpUrl()

    @Test
    fun `保存と復元後も発行元と有効期限を保持する`() {
        val session = assertNotNull(SessionCookie.fromResponse(url, "user_session=token; Path=/; Max-Age=3600; HttpOnly; Secure"))
        val restored = Json.decodeFromString(SessionCookie.serializer(), Json.encodeToString(SessionCookie.serializer(), session))

        assertEquals("token", restored.forRequest(url, System.currentTimeMillis())?.value)
        assertEquals(session.parse()?.expiresAt, restored.parse()?.expiresAt)
        assertEquals(true, restored.parse()?.hostOnly)
        assertEquals(true, restored.parse()?.httpOnly)
    }

    @Test
    fun `別ホスト別ポートHTTPにはセッションを送らない`() {
        val session = assertNotNull(SessionCookie.fromResponse(url, "user_session=token; Path=/; Domain=example.com"))

        assertNull(session.forRequest("https://sub.example.com/graphql".toHttpUrl(), 0))
        assertNull(session.forRequest("https://other.com/graphql".toHttpUrl(), 0))
        assertNull(session.forRequest("https://example.com:8443/graphql".toHttpUrl(), 0))
        assertNull(session.forRequest("http://example.com/graphql".toHttpUrl(), 0))
    }

    @Test
    fun `失効Cookieと別名Cookieと対象外パスは送らない`() {
        val expired = assertNotNull(SessionCookie.fromResponse(url, "user_session=token; Path=/; Max-Age=0"))
        val scoped = assertNotNull(SessionCookie.fromResponse(url, "user_session=token; Path=/graphql"))

        assertNull(expired.forRequest(url, System.currentTimeMillis()))
        assertNull(SessionCookie.fromResponse(url, "other=token; Path=/"))
        assertNull(SessionCookie.fromResponse(url, "user_session=token; Path=/other"))
        assertNull(scoped.forRequest("https://example.com/other".toHttpUrl(), 0))
    }
}
