package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.logic.RemoteIconProxy
import net.matsudamper.kmp.activitypub.logic.TestProfileIcons

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class RemoteIconProxyRoutesTest {
    @Test
    fun `版があれば一年間の immutable とし版がなければ一日間キャッシュする`() =
        testApplication {
            val bytes = TestProfileIcons.read("400x400.png")
            val remoteUrl = "https://remote.example/icon.png"
            val proxy =
                RemoteIconProxy(
                    origin = "http://localhost",
                    signingKey = "test-key".encodeToByteArray(),
                    fetcher = { url, _ ->
                        assertEquals(remoteUrl, url)
                        bytes
                    },
                )
            application {
                routing { remoteIconProxyRoutes(proxy) }
            }

            val unversioned = checkNotNull(proxy.proxyUrlOf(remoteUrl))
            val versioned = checkNotNull(proxy.proxyUrlOf(remoteUrl, Instant.parse("2026-09-01T00:00:00Z")))
            for ((url, cacheControl) in listOf(
                unversioned to "public, max-age=86400",
                versioned to "public, max-age=31536000, immutable",
                "$unversioned?v=arbitrary" to "public, max-age=31536000, immutable",
            )) {
                val response = client.get(url)
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(cacheControl, response.headers[HttpHeaders.CacheControl])
                assertEquals("nosniff", response.headers["X-Content-Type-Options"])
                assertContentEquals(bytes, response.bodyAsBytes())
            }
            val invalid = client.get("/proxy/icon/invalid/invalid.bin?v=version")
            assertEquals(HttpStatusCode.NotFound, invalid.status)
            assertEquals("no-store", invalid.headers[HttpHeaders.CacheControl])
        }
}
