package net.matsudamper.kmp.activitypub.federation

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import net.matsudamper.kotpub.http.ActivityPubClientResponse
import net.matsudamper.kotpub.http.ActivityPubHttpClient
import net.matsudamper.kotpub.http.ActivityPubPostResponse
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import okhttp3.Dns
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

/**
 * 相手のサーバーが書いた URL に取りに行くので、内部のアドレスに繋がせない。
 * リダイレクト先も [PublicAddressDns] を通る
 */
internal class OkHttpActivityPubClient : ActivityPubHttpClient {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .dns(PublicAddressDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (status: Int, finalUrl: String) -> Boolean,
    ): ActivityPubClientResponse {
        val request = Request.Builder().url(url).headers(withUserAgent(headers).toHeaders()).get().build()
        return runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val body =
                    if (readsBody(response.code, finalUrl)) {
                        runCatching { readAtMost(response.body.source(), maxBodyBytes + 1L) }
                    } else {
                        null
                    }
                ActivityPubClientResponse(status = response.code, finalUrl = finalUrl, body = body)
            }
        }
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): ActivityPubPostResponse {
        // Content-Type はヘッダーで渡ってくる。RequestBody 側に付けると重複する
        val request = Request.Builder().url(url).headers(withUserAgent(headers).toHeaders()).post(body.toRequestBody()).build()
        return runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                ActivityPubPostResponse(status = response.code)
            }
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    /**
     * 上限を 1 バイト超えたところで止める。超えたかどうかは呼び出し側が長さで見る
     */
    private fun readAtMost(
        source: BufferedSource,
        limit: Long,
    ): ByteArray {
        source.request(limit)
        return source.readByteArray(minOf(source.buffer.size, limit))
    }

    private fun withUserAgent(headers: Map<String, String>): Map<String, String> = mapOf(USER_AGENT_HEADER to USER_AGENT) + headers

    private object PublicAddressDns : Dns {
        // OkHttp の Dns は suspend にできない。呼ばれるのは OkHttp の接続用スレッドなので塞いでよい
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = "kmp-activitypub"
    }
}
