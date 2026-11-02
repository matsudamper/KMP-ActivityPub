package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import java.net.URI
import net.matsudamper.kmp.activitypub.logic.LinkPreviewPageFetcher
import net.matsudamper.kotpub.http.ActivityPubHttpClient
import net.matsudamper.kotpub.http.PublicAddressDnsResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 投稿に書かれた URL を取りに行くので、内部のアドレスを弾く [OkHttpActivityPubClient] を通す
 */
internal class HttpLinkPreviewPageFetcher(
    private val client: ActivityPubHttpClient,
) : LinkPreviewPageFetcher {
    override suspend fun fetch(url: String): Document? {
        val host = runCatching { URI(url).host }.getOrNull()?.removeSurrounding("[", "]") ?: return null
        val response =
            try {
                // IP アドレスがそのまま書かれた URL は OkHttp が名前を引かないので、Dns での確認を通らない
                PublicAddressDnsResolver.resolve(host)
                client.get(
                    url = url,
                    headers = mapOf("Accept" to "text/html,application/xhtml+xml"),
                    maxBodyBytes = MAX_BODY_BYTES,
                    readsBody = { status, _ -> status == 200 },
                )
            } catch (_: IOException) {
                return null
            } catch (_: IllegalArgumentException) {
                // OkHttp が URL として読めないとき
                return null
            }
        // OGP は <head> にあるので、上限で切れていても読めるところまで使う
        val body = response.body?.getOrNull() ?: return null
        // 文字コードは <meta charset> を見て決める。書かれていなければ UTF-8
        return body.inputStream().use { Jsoup.parse(it, null, response.finalUrl) }
    }

    private companion object {
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}
