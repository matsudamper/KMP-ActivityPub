package net.matsudamper.kmp.activitypub.android.session

import java.io.IOException
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptor
import com.apollographql.apollo.network.http.HttpInterceptorChain
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class SessionHttpInterceptor(
    private val sessions: SessionRepository,
) : HttpInterceptor {
    override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
        val url = request.url.toHttpUrl()
        if (!url.isHttps) throw IOException("セッションの通信にはHTTPSが必要")
        val session = sessions.forRequest(url, System.currentTimeMillis())
        val builder = request.newBuilder().headers(request.headers.filterNot { it.name.equals("Cookie", ignoreCase = true) })
        if (session.cookieHeader != null) builder.addHeader("Cookie", session.cookieHeader)
        val response = chain.proceed(builder.build())
        try {
            val received = response.headers
                .filter { it.name.equals("Set-Cookie", ignoreCase = true) }
                .mapNotNull { SessionCookie.fromResponse(url, it.value) }
                .lastOrNull()
            if (received != null) {
                sessions.receive(session.revision, received, System.currentTimeMillis())
            } else if (response.statusCode == 401 && session.cookieHeader != null) {
                sessions.receive(session.revision, null, System.currentTimeMillis())
            }
        } catch (error: Exception) {
            response.body?.close()
            throw error
        }
        return response
    }
}
