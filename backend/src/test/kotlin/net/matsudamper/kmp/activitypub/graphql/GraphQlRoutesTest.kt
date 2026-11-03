package net.matsudamper.kmp.activitypub.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ChannelWriterContent
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.writeFully
import net.matsudamper.kmp.activitypub.TestDependencies
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.module

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class GraphQlRoutesTest {
    @Test
    fun `ボディが大きすぎれば 413 で読まない`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("x".repeat(3 * 1024 * 1024 + 1))
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `Content-Length の無いボディでも大きすぎれば 413`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        ChannelWriterContent(
                            body = { writeFully(ByteArray(3 * 1024 * 1024 + 1) { 'x'.code.toByte() }) },
                            contentType = ContentType.Application.Json,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `JSON 以外の Content-Type は 415 で実行しない`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Text.Plain)
                    setBody("""{"query":"query { user { session { account { username } } } }"}""")
                }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }

    @Test
    fun `multipart は Apollo-Require-Preflight が無ければ 400 で実行しない`() =
        testApplication {
            application { module(TestDependencies()) }

            val response = client.submitFormWithBinaryData(url = GRAPHQL_PATH, formData = uploadForm(ByteArray(1)))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `multipart のファイルが投稿の画像の上限を超えたら 413`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.submitFormWithBinaryData(url = GRAPHQL_PATH, formData = uploadForm(ByteArray(NotePostService.MAX_IMAGE_BYTES + 1))) {
                    header("Apollo-Require-Preflight", "true")
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `Content-Length の無い multipart で上限以内のファイルを何枚も送られても、本文全体の上限を超えたら 413`() =
        testApplication {
            application { module(TestDependencies()) }

            val fileSize = NotePostService.MAX_IMAGE_BYTES / 2
            val multipart =
                MultiPartFormDataContent(
                    formData {
                        repeat(3) { index ->
                            append(
                                "$index",
                                ByteArray(fileSize),
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(HttpHeaders.ContentDisposition, "filename=\"image$index\"")
                                },
                            )
                        }
                    },
                )
            val response =
                client.post(GRAPHQL_PATH) {
                    header("Apollo-Require-Preflight", "true")
                    setBody(ChannelWriterContent(body = { multipart.writeTo(this) }, contentType = multipart.contentType))
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `multipart のファイルを変数に入れて実行する`() =
        testApplication {
            application { module(TestDependencies()) }

            val response =
                client.submitFormWithBinaryData(url = GRAPHQL_PATH, formData = uploadForm(ByteArray(1))) {
                    header("Apollo-Require-Preflight", "true")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            // ログインしていないので、ファイルを受け取ったうえで失敗を返す
            assertTrue(response.bodyAsText().contains("NOT_LOGGED_IN"), response.bodyAsText())
        }

    private fun uploadForm(image: ByteArray) =
        formData {
            append(
                "operations",
                """{"query":"mutation(${'$'}image: Upload!) { user { uploadNoteImage(image: ${'$'}image) { imageId failure } } }","variables":{"image":null}}""",
            )
            append("map", """{"0":["variables.image"]}""")
            append(
                "0",
                image,
                Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"image\"")
                },
            )
        }
}
