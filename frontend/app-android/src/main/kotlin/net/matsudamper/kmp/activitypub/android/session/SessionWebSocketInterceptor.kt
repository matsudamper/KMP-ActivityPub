package net.matsudamper.kmp.activitypub.android.session

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * WebSocket の接続要求に Cookie を付ける。接続後の応答ではセッションを更新しないので、更新は HTTP の通信に任せる
 */
internal class SessionWebSocketInterceptor(
    private val sessionRepository: SessionRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) throw IOException("セッションの通信にはHTTPSが必要")
        // OkHttp の通信スレッドで呼ばれるので止めてよい
        val session = runBlocking { sessionRepository.forRequest(request.url, System.currentTimeMillis()) }
        val builder = request.newBuilder().removeHeader("Cookie")
        if (session.cookieHeader != null) builder.header("Cookie", session.cookieHeader)
        return chain.proceed(builder.build())
    }
}
