package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 投稿やリンク先のページに書かれた、誰が決めたか分からない URL を取りに行く。
 *
 * 取得した中身は画面へ返すので、内側のアドレスに届くと中身を外へ出す口になる。
 * IP アドレスがそのまま書かれた URL は OkHttp が名前を引かず [PublicAddressDns] を通らないので、
 * リダイレクトは自分で辿り、接続する前に毎回宛先を確かめる
 */
internal class PublicAddressHttpClient : AutoCloseable {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            // プロキシを通すと名前解決がプロキシ側に移り、PublicAddressDns を通らない
            .proxy(Proxy.NO_PROXY)
            .dns(PublicAddressDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(15))
            .build()

    /**
     * 200 で返ったものだけ返す。取れなければ null
     *
     * @param maxBytes 読むのはここまで。超えていたかは [Response.bytes] が [maxBytes] より長いかで分かる
     */
    suspend fun get(
        url: String,
        accept: String,
        maxBytes: Int,
    ): Response? {
        var target = url.toHttpUrlOrNull() ?: return null
        repeat(MAX_REDIRECTS + 1) {
            if (!isPublic(target)) return null
            when (val result = getOnce(target, accept, maxBytes)) {
                is FetchResult.Redirect -> target = result.location ?: return null
                is FetchResult.Found -> return result.response
                FetchResult.Failure -> return null
            }
        }
        return null
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private suspend fun isPublic(url: HttpUrl): Boolean =
        try {
            PublicAddressDnsResolver.resolve(url.host).isNotEmpty()
        } catch (_: IOException) {
            false
        }

    private suspend fun getOnce(
        url: HttpUrl,
        accept: String,
        maxBytes: Int,
    ): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return try {
            withOpenTelemetryContext {
                runInterruptible {
                    client.newCall(request).execute().use { response ->
                        when {
                            response.isRedirect -> FetchResult.Redirect(response.header("Location")?.let { url.resolve(it) })

                            response.code != 200 -> FetchResult.Failure

                            else -> {
                                val readLimit = maxBytes + 1L
                                val source = response.body.source()
                                source.request(readLimit)
                                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, readLimit))
                                FetchResult.Found(Response(bytes = bytes, finalUrl = url.toString()))
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            FetchResult.Failure
        }
    }

    class Response(
        val bytes: ByteArray,
        val finalUrl: String,
    )

    private sealed interface FetchResult {
        class Redirect(val location: HttpUrl?) : FetchResult

        class Found(val response: Response) : FetchResult

        data object Failure : FetchResult
    }

    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "kmp-activitypub"
    }
}
