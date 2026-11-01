package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class RemoteIconProxyTest {
    private val remoteIcons = mutableMapOf<String, ByteArray>()
    private val fetchedUrls = mutableListOf<String>()
    private val proxy = createProxy(signingKey = "key-1".encodeToByteArray())

    @Test
    fun `発行した中継 URL で相手の画像を返す`() {
        val png = TestProfileIcons.read("400x400.png")
        remoteIcons[REMOTE_URL] = png

        val result = fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL)))

        val found = assertIs<RemoteIconProxyResult.Found>(result)
        assertContentEquals(png, found.bytes)
        assertEquals("image/png", found.contentType)
        assertEquals(listOf(REMOTE_URL), fetchedUrls)
    }

    @Test
    fun `中継 URL はこのサーバーの origin で、拡張子は bin`() {
        val proxyUrl = checkNotNull(proxy.proxyUrlOf(REMOTE_URL))

        assertEquals(true, proxyUrl.startsWith("https://example.com/proxy/icon/"))
        assertEquals(true, proxyUrl.endsWith(".bin"))
        assertEquals(proxyUrl, proxy.proxyUrlOf(REMOTE_URL))
    }

    @Test
    fun `更新時刻だけが違う中継 URL は版だけが変わり同じ署名で元 URL を取得する`() {
        remoteIcons[REMOTE_URL] = TestProfileIcons.read("400x400.png")
        val updatedAt = Instant.parse("2026-09-01T00:00:00.123456789Z")
        val unversioned = checkNotNull(proxy.proxyUrlOf(REMOTE_URL))
        val versioned = checkNotNull(proxy.proxyUrlOf(REMOTE_URL, updatedAt))
        val nextVersion = checkNotNull(proxy.proxyUrlOf(REMOTE_URL, updatedAt.plusNanos(1)))

        assertEquals(unversioned, versioned.substringBefore('?'))
        assertEquals(versioned, proxy.proxyUrlOf(REMOTE_URL, updatedAt))
        assertNotEquals(versioned, nextVersion)
        assertEquals(true, versioned.substringAfter("?v=").matches(Regex("[A-Za-z0-9_-]+")))
        assertIs<RemoteIconProxyResult.Found>(fetch(versioned))
        assertIs<RemoteIconProxyResult.Found>(fetch(nextVersion))
        assertEquals(listOf(REMOTE_URL, REMOTE_URL), fetchedUrls)
    }

    @Test
    fun `http と https 以外の URL は中継しない`() {
        assertNull(proxy.proxyUrlOf("file:///etc/passwd"))
        assertNull(proxy.proxyUrlOf("data:image/png;base64,AAAA"))
        assertNull(proxy.proxyUrlOf("not a url"))
        assertNotNull(proxy.proxyUrlOf("http://remote.example/icon.png"))
    }

    @Test
    fun `署名が合わない URL は取りに行かない`() {
        remoteIcons[REMOTE_URL] = TestProfileIcons.read("400x400.png")
        val (encodedUrl, _) = split(checkNotNull(proxy.proxyUrlOf(REMOTE_URL)))
        val (_, otherSignature) = split(checkNotNull(proxy.proxyUrlOf("https://remote.example/other.png")))

        assertEquals(RemoteIconProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = encodedUrl, signature = otherSignature))
        assertEquals(RemoteIconProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = encodedUrl, signature = ""))
        assertEquals(RemoteIconProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = "!!!", signature = otherSignature))
        assertEquals(listOf(), fetchedUrls)
    }

    @Test
    fun `別の鍵で発行した URL は受け付けない`() {
        remoteIcons[REMOTE_URL] = TestProfileIcons.read("400x400.png")
        val otherProxy = createProxy(signingKey = "key-2".encodeToByteArray())
        val otherProxyUrl = checkNotNull(otherProxy.proxyUrlOf(REMOTE_URL))

        assertNotEquals(proxy.proxyUrlOf(REMOTE_URL), otherProxyUrl)
        assertEquals(RemoteIconProxyResult.NotIssued, fetch(otherProxyUrl))
    }

    @Test
    fun `画像として受け付けない形式は返さない`() {
        remoteIcons[REMOTE_URL] = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".encodeToByteArray()

        assertEquals(RemoteIconProxyResult.Unavailable, fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL))))
    }

    @Test
    fun `相手から取れなければ Unavailable`() {
        assertEquals(RemoteIconProxyResult.Unavailable, fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL))))
    }

    private fun createProxy(signingKey: ByteArray): RemoteIconProxy =
        RemoteIconProxy(
            origin = "https://example.com",
            signingKey = signingKey,
            fetcher = { url, _ ->
                fetchedUrls += url
                remoteIcons[url]
            },
        )

    private fun fetch(proxyUrl: String): RemoteIconProxyResult {
        val (encodedUrl, signature) = split(proxyUrl)
        return proxy.fetchBlocking(encodedUrl = encodedUrl, signature = signature)
    }

    private fun split(proxyUrl: String): Pair<String, String> {
        val segments = proxyUrl.substringBefore("?").removeSuffix(".bin").split('/')
        return segments[segments.size - 2] to segments.last()
    }

    private fun RemoteIconProxy.fetchBlocking(
        encodedUrl: String,
        signature: String,
    ): RemoteIconProxyResult = runBlocking { fetch(encodedUrl = encodedUrl, signature = signature) }

    private companion object {
        const val REMOTE_URL = "https://remote.example/media/icon.png"
    }
}
