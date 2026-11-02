package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import net.matsudamper.kmp.activitypub.logic.LinkPreviewPageFetcher
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 投稿に書かれた URL を取りに行く。
 *
 * 取得した HTML の title などは画面に返すので、内側のアドレスに届くと中身を外へ出す口になる。
 * IP アドレスがそのまま書かれた URL は OkHttp が名前を引かず [PublicAddressDns] を通らないので、
 * リダイレクトは自分で辿り、接続する前に毎回宛先を確かめる
 */
internal class HttpLinkPreviewPageFetcher : LinkPreviewPageFetcher, AutoCloseable {
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

    override suspend fun fetch(url: String): Document? {
        var target = url.toHttpUrlOrNull() ?: return null
        repeat(MAX_REDIRECTS + 1) {
            if (!isPublic(target)) return null
            when (val result = get(target)) {
                is FetchResult.Redirect -> target = result.location ?: return null
                is FetchResult.Page -> return result.document
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

    private suspend fun get(url: HttpUrl): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
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
                                val source = response.body.source()
                                source.request(MAX_BODY_BYTES)
                                // OGP は <head> にあるので、上限で切れていても読めるところまで使う
                                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BODY_BYTES))
                                // 文字コードは <meta charset> を見て決める。書かれていなければ UTF-8
                                FetchResult.Page(bytes.inputStream().use { Jsoup.parse(it, null, url.toString()) })
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            FetchResult.Failure
        }
    }

    private sealed interface FetchResult {
        class Redirect(val location: HttpUrl?) : FetchResult

        class Page(val document: Document) : FetchResult

        data object Failure : FetchResult
    }

    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runBlocking { PublicAddressDnsResolver.resolve(hostname) }
                .map { InetAddress.getByName(it) }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_BYTES = 1024L * 1024L
        const val USER_AGENT = "kmp-activitypub"
    }
}
