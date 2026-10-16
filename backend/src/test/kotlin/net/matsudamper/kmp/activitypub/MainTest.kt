package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir

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
                module(TestDependencies(env = ServerEnv(mapOf("STATIC_SRC_DIR" to tempDir.toString()))))
            }

            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html><body>Hello</body></html>", response.bodyAsText())
        }

    @Test
    fun `Android アプリを設定すると assetlinks json を返す`() =
        testApplication {
            application {
                module(
                    TestDependencies(
                        env = ServerEnv(mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app", "PASSKEY_ANDROID_CERT_SHA256" to "AA:BB")),
                    ),
                )
            }

            val response = client.get("/.well-known/assetlinks.json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """[{"relation":["delegate_permission/common.get_login_creds"],""" +
                    """"target":{"namespace":"android_app","package_name":"com.example.app","sha256_cert_fingerprints":["AA:BB"]}}]""",
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
}
