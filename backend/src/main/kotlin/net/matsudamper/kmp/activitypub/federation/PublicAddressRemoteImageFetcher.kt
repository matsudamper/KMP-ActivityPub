package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.RemoteImageFetcher

/**
 * リンク先のページが名乗った画像を取りに行く。中身を画面へ返すので [PublicAddressHttpClient] を通す
 */
internal class PublicAddressRemoteImageFetcher(
    private val client: PublicAddressHttpClient,
) : RemoteImageFetcher {
    override suspend fun fetch(
        url: String,
        maxBytes: Int,
    ): ByteArray? {
        val response = client.get(url = url, accept = "image/*", maxBytes = maxBytes) ?: return null
        return response.bytes.takeIf { it.size <= maxBytes }
    }
}
