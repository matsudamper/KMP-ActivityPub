package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.time.Duration
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 返信先の URL など、他のサーバーが書いた URL も取りに行くので、内側のアドレスに届かないようにする。
 * IP アドレスがそのまま書かれた URL は OkHttp が名前を引かず [PublicAddressDns] を通らないので、
 * リダイレクトは自分で辿り、接続する前に毎回宛先を確かめる
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
            .callTimeout(Duration.ofSeconds(30))
            .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): ActivityPubClientResponse {
        var target = url.toHttpUrlOrNull() ?: throw IOException("URL として読めない: $url")
        repeat(MAX_REDIRECTS + 1) {
            requirePublic(target)
            when (val result = getOnce(target, headers, maxBodyBytes, readsBody)) {
                is GetResult.Redirect -> target = result.location ?: throw IOException("リダイレクト先が無い: $target")
                is GetResult.Completed -> return result.response
            }
        }
        throw IOException("リダイレクトが多すぎる: $url")
    }

    private suspend fun getOnce(
        url: HttpUrl,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): GetResult {
        val request = Request.Builder()
            .url(url)
            .headers(withUserAgent(headers).toHeaders())
            .get()
            .build()
        return execute(request) { response ->
            if (response.isRedirect) return@execute GetResult.Redirect(response.header("Location")?.let { url.resolve(it) })
            val finalUrl = url.toString()
            val body =
                if (readsBody(response.code, finalUrl)) {
                    try {
                        val source = response.body.source()
                        source.request(maxBodyBytes + 1L)
                        ResponseBody.Read(source.buffer.readByteArray(minOf(source.buffer.size, maxBodyBytes + 1L)))
                    } catch (failure: IOException) {
                        Span.current().recordException(failure)
                        ResponseBody.Unreadable(failure)
                    }
                } else {
                    ResponseBody.NotRead
                }
            GetResult.Completed(ActivityPubClientResponse(status = response.code, finalUrl = finalUrl, body = body))
        }
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): ActivityPubPostResponse {
        requirePublic(url.toHttpUrlOrNull() ?: throw IOException("URL として読めない: $url"))
        val request = Request.Builder()
            .url(url)
            .headers(withUserAgent(headers).toHeaders())
            // Content-Type は署名されたヘッダに入っているので、ここで付け直さない
            .post(body.toRequestBody())
            .build()
        return execute(request) { response -> ActivityPubPostResponse(status = response.code) }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private suspend fun requirePublic(url: HttpUrl) {
        if (PublicAddressDnsResolver.resolve(url.host).isEmpty()) throw IOException("公開されていない宛先: ${url.host}")
    }

    private suspend fun <T> execute(
        request: Request,
        block: (Response) -> T,
    ): T = withOpenTelemetryContext {
        runInterruptible {
            client.newCall(request).execute().use(block)
        }
    }

    private fun withUserAgent(headers: Map<String, String>): Map<String, String> =
        if (headers.keys.any { it.equals(USER_AGENT_HEADER, ignoreCase = true) }) {
            headers
        } else {
            headers + (USER_AGENT_HEADER to USER_AGENT)
        }

    private sealed interface GetResult {
        class Redirect(val location: HttpUrl?) : GetResult

        class Completed(val response: ActivityPubClientResponse) : GetResult
    }

    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = "kmp-activitypub"
        const val MAX_REDIRECTS = 5
    }
}
