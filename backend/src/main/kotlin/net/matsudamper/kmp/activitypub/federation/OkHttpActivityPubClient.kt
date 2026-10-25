package net.matsudamper.kmp.activitypub.federation

import java.net.InetAddress
import java.net.Proxy
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.matsudamper.kotpub.http.ActivityPubClientResponse
import net.matsudamper.kotpub.http.ActivityPubHttpClient
import net.matsudamper.kotpub.http.ActivityPubPostResponse
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import okhttp3.Dns
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class OkHttpActivityPubClient : ActivityPubHttpClient {
    private val getClient: OkHttpClient =
        OkHttpClient.Builder()
            // プロキシを通すと名前解決がプロキシ側に移り、PublicAddressDnsResolver を通らない
            .proxy(Proxy.NO_PROXY)
            .dns(PublicAddressDns)
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(30))
            .build()

    private val postClient: OkHttpClient =
        getClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): ActivityPubClientResponse {
        val request = Request.Builder()
            .url(url)
            .headers(withUserAgent(headers).toHeaders())
            .get()
            .build()
        return execute(getClient, request) { response ->
            val finalUrl = response.request.url.toString()
            val body =
                if (readsBody(response.code, finalUrl)) {
                    runCatching {
                        val source = response.body.source()
                        source.request(maxBodyBytes + 1L)
                        source.buffer.readByteArray(minOf(source.buffer.size, maxBodyBytes + 1L))
                    }
                } else {
                    null
                }
            ActivityPubClientResponse(status = response.code, finalUrl = finalUrl, body = body)
        }
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
        return execute(postClient, request) { response -> ActivityPubPostResponse(status = response.code) }
    }

    override fun close() {
        getClient.dispatcher.executorService.shutdown()
        getClient.connectionPool.evictAll()
    }

    private suspend fun <T> execute(
        client: OkHttpClient,
        request: Request,
        block: (okhttp3.Response) -> T,
    ): T = withContext(Dispatchers.IO) {
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

    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = "kmp-activitypub"
    }
}
