package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.RemoteImageBody
import net.matsudamper.kmp.activitypub.logic.RemoteImageFetcher

/**
 * 他のサーバーやリンク先のページが名乗った画像を取りに行く。中身を画面へ返すので [PublicAddressHttpClient] を通す
 */
internal class PublicAddressRemoteImageFetcher(
    private val client: PublicAddressHttpClient,
) : RemoteImageFetcher {
    override suspend fun open(url: String): RemoteImageBody? = client.open(url = url, accept = "image/*")
}
