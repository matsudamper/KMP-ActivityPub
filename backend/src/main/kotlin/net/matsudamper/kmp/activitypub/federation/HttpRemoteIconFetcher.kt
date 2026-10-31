package net.matsudamper.kmp.activitypub.federation

import java.io.IOException
import net.matsudamper.kmp.activitypub.logic.RemoteIconFetcher
import net.matsudamper.kotpub.http.ActivityPubHttpClient

/**
 * 相手が決めた URL を取りに行くので、内部のアドレスを弾く [OkHttpActivityPubClient] を通す
 */
internal class HttpRemoteIconFetcher(
    private val client: ActivityPubHttpClient,
) : RemoteIconFetcher {
    override suspend fun fetch(
        url: String,
        maxBytes: Int,
    ): ByteArray? {
        val response =
            try {
                client.get(
                    url = url,
                    headers = mapOf("Accept" to "image/*"),
                    maxBodyBytes = maxBytes,
                    readsBody = { status, _ -> status == 200 },
                )
            } catch (_: IOException) {
                return null
            } catch (_: IllegalArgumentException) {
                // OkHttp が URL として読めないとき
                return null
            }
        val body = response.body?.getOrNull() ?: return null
        return body.takeIf { it.size <= maxBytes }
    }
}
