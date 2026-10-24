package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class MainTest {
    @Test
    fun `healthz で ok を返す`() =
        testApplication {
            application {
                module(TestDependencies())
            }

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
        }

    @Test
    fun `hello で Hello World を返す`() =
        testApplication {
            application {
                module(TestDependencies())
            }

            val response = client.get("/hello")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello World", response.bodyAsText())
        }

    @Test
    fun `STATIC_SRC_DIR が無いときは 404 を返す`() =
        testApplication {
            application {
                module(TestDependencies())
            }

            val response = client.get("/")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `静的ファイルがあるときは index html を返す`(@TempDir tempDir: Path) =
        testApplication {
            tempDir.createDirectories()
            tempDir.resolve("index.html").writeText("<html><body>Hello</body></html>")

            application {
                module(TestDependencies(env = ServerEnv(TestEnv.REQUIRED + mapOf("STATIC_SRC_DIR" to tempDir.toString()))))
            }

            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html><body>Hello</body></html>", response.bodyAsText())
        }

    @Test
    fun `ページを開くときだけ無いパスに index html を返す`(@TempDir tempDir: Path) =
        testApplication {
            tempDir.createDirectories()
            tempDir.resolve("index.html").writeText("<html><body>Hello</body></html>")

            application {
                module(TestDependencies(env = ServerEnv(TestEnv.REQUIRED + mapOf("STATIC_SRC_DIR" to tempDir.toString()))))
            }

            val pageResponse = client.get("/@john.doe") { header(HttpHeaders.Accept, "text/html,*/*;q=0.8") }
            assertEquals(HttpStatusCode.OK, pageResponse.status)
            assertEquals("<html><body>Hello</body></html>", pageResponse.bodyAsText())

            val scriptResponse = client.get("/missing.js") { header(HttpHeaders.Accept, "*/*") }
            assertEquals(HttpStatusCode.NotFound, scriptResponse.status)
        }

    @Test
    fun `Android アプリを設定すると assetlinks json を返す`() =
        testApplication {
            application {
                module(
                    TestDependencies(
                        env = ServerEnv(TestEnv.REQUIRED + mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app", "PASSKEY_ANDROID_CERT_SHA256" to FINGERPRINT)),
                    ),
                )
            }

            val response = client.get("/.well-known/assetlinks.json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """[{"relation":["delegate_permission/common.handle_all_urls","delegate_permission/common.get_login_creds"],""" +
                    """"target":{"namespace":"android_app","package_name":"com.example.app","sha256_cert_fingerprints":["$FINGERPRINT"]}}]""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `Android アプリが未設定なら assetlinks json は 404`() =
        testApplication {
            application {
                module(TestDependencies())
            }

            assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
        }

    @Test
    fun `S3 を有効にすると OIDC の Discovery と公開鍵を返す`() =
        testApplication {
            val privateJwk = RSAKeyGenerator(2048).keyID("test-kid").generate()
            application {
                module(
                    TestDependencies(
                        env = ServerEnv(
                            TestEnv.REQUIRED + mapOf(
                                "ENABLE_S3" to "true",
                                "OIDC_ISSUER" to "https://example.com/",
                                "OIDC_JWK_PRIVATE" to privateJwk.toJSONString(),
                                "S3_BUCKET" to "bucket",
                                "S3_REGION" to "ap-northeast-1",
                                "S3_ROLE_ARN" to "arn:aws:iam::123456789012:role/image",
                                "S3_AUDIENCE" to "sts.amazonaws.com",
                                "S3_ENDPOINT" to "",
                                "STS_ENDPOINT" to "",
                            ),
                        ),
                    ),
                )
            }

            val discovery = client.get("/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, discovery.status)
            assertEquals(
                """{"issuer":"https://example.com","jwks_uri":"https://example.com/jwks",""" +
                    """"response_types_supported":["id_token"],"subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"]}""",
                discovery.bodyAsText(),
            )

            val jwks = client.get("/jwks")
            assertEquals(HttpStatusCode.OK, jwks.status)
            assertEquals(JWKSet(privateJwk.toPublicJWK()), JWKSet.parse(jwks.bodyAsText()))
        }

    @Test
    fun `S3 が無効なら OIDC の Discovery は 404`() =
        testApplication {
            application {
                module(TestDependencies())
            }

            assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/openid-configuration").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/jwks").status)
        }

    private companion object {
        val FINGERPRINT: String = List(32) { "AB" }.joinToString(":")
    }
}
