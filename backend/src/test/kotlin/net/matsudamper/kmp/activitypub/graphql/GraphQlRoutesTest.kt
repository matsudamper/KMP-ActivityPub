package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ChannelWriterContent
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.writeFully
import net.matsudamper.kmp.activitypub.module
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH
import net.matsudamper.kmp.activitypub.testDependencies

// 口そのものの振る舞いを確認する。中身（管理画面のフィールド）は AdminGraphQlTest 側。
class GraphQlRoutesTest {
    @Test
    fun `ボディが大きすぎれば 413 で読まない`() =
        testApplication {
            application { module(testDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("x".repeat(1024 * 1024 + 1))
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `Content-Length の無いボディでも大きすぎれば 413`() =
        testApplication {
            application { module(testDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    // 長さを申告しない chunked で送る
                    setBody(
                        ChannelWriterContent(
                            body = { writeFully(ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() }) },
                            contentType = ContentType.Application.Json,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `上限に収まっていれば読む`() =
        testApplication {
            application { module(testDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"query { admin { session { loggedIn } } }"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }
}
