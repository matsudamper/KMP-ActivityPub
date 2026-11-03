package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.LinkPreviewPageFetcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class HttpLinkPreviewPageFetcher(
    private val client: PublicAddressHttpClient,
) : LinkPreviewPageFetcher {
    override suspend fun fetch(url: String): Document? {
        // OGP は <head> にあるので、上限で切れていても読めるところまで使う
        val response = client.get(url = url, accept = "text/html,application/xhtml+xml", maxBytes = MAX_BODY_BYTES) ?: return null
        // 文字コードは <meta charset> を見て決める。書かれていなければ UTF-8
        return response.bytes.inputStream().use { Jsoup.parse(it, null, response.finalUrl) }
    }

    private companion object {
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}
