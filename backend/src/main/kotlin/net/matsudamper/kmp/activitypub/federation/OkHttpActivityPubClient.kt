package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.ProtocolException
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.http.ActivityPubClientResponse
import net.matsudamper.kotpub.http.ActivityPubHttpClient
import net.matsudamper.kotpub.http.ActivityPubPostResponse
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import net.matsudamper.kotpub.http.ResponseBody
import okhttp3.Dns
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 宛先の URL は相手が決めるので、内側のアドレスへ繋がないようにする。
 * IP アドレスがそのまま書かれた URL は OkHttp が名前を引かず [PublicAddressDns] を通らないので、
 * リダイレクトは自分で辿り、送る前に毎回宛先を確かめる
 */
internal class OkHttpActivityPubClient : ActivityPubHttpClient {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            // プロキシを通すと名前解決がプロキシ側に移り、PublicAddressDnsResolver を通らない
            .proxy(Proxy.NO_PROXY)
            .dns(PublicAddressDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .callTimeout(CALL_TIMEOUT)
            .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): ActivityPubClientResponse {
        var request = Request.Builder()
            .url(url)
            .headers(withUserAgent(headers).toHeaders())
            .get()
            .build()
        // リダイレクトを辿っても期限を延ばさないよう、全体で 1 つの期限を共有する
        val deadlineNanos = System.nanoTime() + CALL_TIMEOUT.toNanos()
        repeat(MAX_REDIRECTS + 1) {
            requirePublicHost(request.url)
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) throw InterruptedIOException("タイムアウトした: $url")
            val result = execute(request, callTimeoutNanos = remainingNanos) { response ->
                val redirectRequest = response.redirectRequest()
                if (redirectRequest != null) {
                    GetResult.Redirect(redirectRequest)
                } else {
                    GetResult.Done(response.toActivityPubClientResponse(maxBodyBytes, readsBody))
                }
            }
            when (result) {
                is GetResult.Redirect -> request = result.request
                is GetResult.Done -> return result.response
            }
        }
        throw ProtocolException("リダイレクトが多すぎる: $url")
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): ActivityPubPostResponse {
        val request = Request.Builder()
            .url(url)
            .headers(withUserAgent(headers).toHeaders())
            // Content-Type は署名されたヘッダに入っているので、ここで付け直さない
            .post(body.toRequestBody())
            .build()
        requirePublicHost(request.url)
        return execute(request, callTimeoutNanos = CALL_TIMEOUT.toNanos()) { response -> ActivityPubPostResponse(status = response.code) }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    /**
     * 公開アドレスでなければ [net.matsudamper.kotpub.http.NonPublicAddressException] を投げる
     */
    private suspend fun requirePublicHost(url: HttpUrl) {
        PublicAddressDnsResolver.resolve(url.host)
    }

    /**
     * OkHttp が自分で辿るときと同じく、ホスト・ポート・スキームのどれかが変わるなら Authorization を外す
     *
     * @return リダイレクトでないか、移り先が無ければ null
     */
    private fun Response.redirectRequest(): Request? {
        if (!isRedirect) return null
        val location = header("Location")?.let { request.url.resolve(it) } ?: return null
        return request.newBuilder()
            .url(location)
            .apply { if (!location.isSameOrigin(request.url)) removeHeader("Authorization") }
            .build()
    }

    private fun Response.toActivityPubClientResponse(
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): ActivityPubClientResponse {
        val finalUrl = request.url.toString()
        val readBody =
            if (readsBody(code, finalUrl)) {
                try {
                    val source = body.source()
                    source.request(maxBodyBytes + 1L)
                    ResponseBody.Read(source.buffer.readByteArray(minOf(source.buffer.size, maxBodyBytes + 1L)))
                } catch (failure: IOException) {
                    Span.current().recordException(failure)
                    ResponseBody.Unreadable(failure)
                }
            } else {
                ResponseBody.NotRead
            }
        return ActivityPubClientResponse(status = code, finalUrl = finalUrl, body = readBody)
    }

    private fun HttpUrl.isSameOrigin(other: HttpUrl): Boolean =
        host == other.host && port == other.port && scheme == other.scheme

    private suspend fun <T> execute(
        request: Request,
        callTimeoutNanos: Long,
        block: (Response) -> T,
    ): T = withOpenTelemetryContext {
        runInterruptible {
            val call = client.newCall(request)
            call.timeout().timeout(callTimeoutNanos, TimeUnit.NANOSECONDS)
            call.execute().use(block)
        }
    }

    private fun withUserAgent(headers: Map<String, String>): Map<String, String> =
        if (headers.keys.any { it.equals(USER_AGENT_HEADER, ignoreCase = true) }) {
            headers
        } else {
            headers + (USER_AGENT_HEADER to USER_AGENT)
        }

    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private sealed interface GetResult {
        class Redirect(val request: Request) : GetResult

        class Done(val response: ActivityPubClientResponse) : GetResult
    }

    private companion object {
        // OkHttp が自分で辿るときの上限に合わせる
        const val MAX_REDIRECTS = 20
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(30)
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = "kmp-activitypub"
    }
}
