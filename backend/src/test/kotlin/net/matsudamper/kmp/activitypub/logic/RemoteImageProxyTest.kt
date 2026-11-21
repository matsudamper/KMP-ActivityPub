package net.matsudamper.kmp.activitypub.logic

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class RemoteImageProxyTest {
    private val remoteIcons = mutableMapOf<String, ByteArray>()
    private val fetchedUrls = mutableListOf<String>()
    private val proxy = createProxy(signingKey = "key-1".encodeToByteArray())

    @Test
    fun `発行した中継 URL で相手の画像を返す`() {
        val png = TestProfileIcons.read("400x400.png")
        remoteIcons[REMOTE_URL] = png

        val result = fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL)))

        val found = assertIs<FoundImage>(result)
        assertContentEquals(png, found.bytes)
        assertEquals("image/png", found.contentType)
        assertEquals(listOf(REMOTE_URL), fetchedUrls)
    }

    @Test
    fun `中継 URL はこのサーバーの origin で、拡張子は bin`() {
        val proxyUrl = checkNotNull(proxy.proxyUrlOf(REMOTE_URL))

        assertEquals(true, proxyUrl.startsWith("https://example.com/proxy/image/"))
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
        assertIs<FoundImage>(fetch(versioned))
        assertIs<FoundImage>(fetch(nextVersion))
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

        assertEquals(RemoteImageProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = encodedUrl, signature = otherSignature))
        assertEquals(RemoteImageProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = encodedUrl, signature = ""))
        assertEquals(RemoteImageProxyResult.NotIssued, proxy.fetchBlocking(encodedUrl = "!!!", signature = otherSignature))
        assertEquals(listOf(), fetchedUrls)
    }

    @Test
    fun `別の鍵で発行した URL は受け付けない`() {
        remoteIcons[REMOTE_URL] = TestProfileIcons.read("400x400.png")
        val otherProxy = createProxy(signingKey = "key-2".encodeToByteArray())
        val otherProxyUrl = checkNotNull(otherProxy.proxyUrlOf(REMOTE_URL))

        assertNotEquals(proxy.proxyUrlOf(REMOTE_URL), otherProxyUrl)
        assertEquals(RemoteImageProxyResult.NotIssued, fetch(otherProxyUrl))
    }

    @Test
    fun `画像として受け付けない形式は返さない`() {
        remoteIcons[REMOTE_URL] = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".encodeToByteArray()

        assertEquals(RemoteImageProxyResult.Unavailable, fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL))))
    }

    @Test
    fun `favicon の ICO は中継する`() {
        val ico = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x10, 0x10, 0x00, 0x00, 0x01, 0x00)
        remoteIcons[REMOTE_URL] = ico

        val found = assertIs<FoundImage>(fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL))))

        assertContentEquals(ico, found.bytes)
        assertEquals("image/x-icon", found.contentType)
    }

    @Test
    fun `相手から取れなければ Unavailable`() {
        assertEquals(RemoteImageProxyResult.Unavailable, fetch(checkNotNull(proxy.proxyUrlOf(REMOTE_URL))))
    }

    @Test
    fun `名乗った大きさが上限を超えていれば読まずに Unavailable`() {
        val png = TestProfileIcons.read("400x400.png")
        val tooLargeProxy =
            RemoteImageProxy(
                origin = "https://example.com",
                signingKey = "key-1".encodeToByteArray(),
                fetcher = { ByteArrayRemoteImageBody(png, contentLength = NotePostService.MAX_IMAGE_BYTES + 1L) },
            )
        val (encodedUrl, signature) = split(checkNotNull(tooLargeProxy.proxyUrlOf(REMOTE_URL)))

        assertEquals(RemoteImageProxyResult.Unavailable, tooLargeProxy.fetchBlocking(encodedUrl = encodedUrl, signature = signature))
    }

    @Test
    fun `大きさを名乗らずに上限を超えたら流している途中で打ち切る`() {
        val png = TestProfileIcons.read("400x400.png")
        remoteIcons[REMOTE_URL] = png + ByteArray(NotePostService.MAX_IMAGE_BYTES)
        val noLengthProxy =
            RemoteImageProxy(
                origin = "https://example.com",
                signingKey = "key-1".encodeToByteArray(),
                fetcher = { url -> remoteIcons[url]?.let { ByteArrayRemoteImageBody(it, contentLength = null) } },
            )
        val (encodedUrl, signature) = split(checkNotNull(noLengthProxy.proxyUrlOf(REMOTE_URL)))

        assertFailsWith<IOException> { noLengthProxy.fetchBlocking(encodedUrl = encodedUrl, signature = signature) }
    }

    @Test
    fun `先頭を読む途中で切れたら Unavailable`() {
        val brokenProxy =
            RemoteImageProxy(
                origin = "https://example.com",
                signingKey = "key-1".encodeToByteArray(),
                fetcher = {
                    object : RemoteImageBody {
                        override val contentLength: Long? = null

                        override suspend fun read(buffer: ByteArray): Int = throw IOException("切断")

                        override fun close() = Unit
                    }
                },
            )
        val (encodedUrl, signature) = split(checkNotNull(brokenProxy.proxyUrlOf(REMOTE_URL)))

        assertEquals(RemoteImageProxyResult.Unavailable, brokenProxy.fetchBlocking(encodedUrl = encodedUrl, signature = signature))
    }

    private fun createProxy(signingKey: ByteArray): RemoteImageProxy =
        RemoteImageProxy(
            origin = "https://example.com",
            signingKey = signingKey,
            fetcher = { url ->
                fetchedUrls += url
                remoteIcons[url]?.let(::ByteArrayRemoteImageBody)
            },
        )

    private fun fetch(proxyUrl: String): Any {
        val (encodedUrl, signature) = split(proxyUrl)
        return proxy.fetchBlocking(encodedUrl = encodedUrl, signature = signature)
    }

    private fun split(proxyUrl: String): Pair<String, String> {
        val segments = proxyUrl.substringBefore("?").removeSuffix(".bin").split('/')
        return segments[segments.size - 2] to segments.last()
    }

    private fun RemoteImageProxy.fetchBlocking(
        encodedUrl: String,
        signature: String,
    ): Any =
        runBlocking {
            fetch(encodedUrl = encodedUrl, signature = signature) { result ->
                if (result is RemoteImageProxyResult.Found) FoundImage(bytes = result.readAll(), contentType = result.contentType) else result
            }
        }

    private suspend fun RemoteImageProxyResult.Found.readAll(): ByteArray {
        val output = ByteArrayOutputStream()
        copyTo { buffer, length -> output.write(buffer, 0, length) }
        return output.toByteArray()
    }

    /**
     * 本文は応答を返し終えると読めなくなるので、読み切った結果で比べる
     */
    private class FoundImage(
        val bytes: ByteArray,
        val contentType: String,
    )

    private companion object {
        const val REMOTE_URL = "https://remote.example/media/icon.png"
    }
}
