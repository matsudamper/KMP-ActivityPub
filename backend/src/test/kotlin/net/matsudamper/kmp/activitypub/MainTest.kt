package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
    fun `ページを開くときだけ無いパスに index html を返す`(@TempDir tempDir: Path) =
        testApplication {
            tempDir.createDirectories()
            tempDir.resolve("index.html").writeText("<html><body>Hello</body></html>")

            application {
                module(TestDependencies(env = ServerEnv(mapOf("STATIC_SRC_DIR" to tempDir.toString()))))
            }

            val pageResponse = client.get("/@john.doe") { header(HttpHeaders.Accept, "text/html,*/*;q=0.8") }
            assertEquals(HttpStatusCode.OK, pageResponse.status)
            assertEquals("<html><body>Hello</body></html>", pageResponse.bodyAsText())

            val scriptResponse = client.get("/missing.js") { header(HttpHeaders.Accept, "*/*") }
            assertEquals(HttpStatusCode.NotFound, scriptResponse.status)
        }
}
