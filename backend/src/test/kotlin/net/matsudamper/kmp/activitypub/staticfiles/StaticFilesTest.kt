package net.matsudamper.kmp.activitypub.staticfiles

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class StaticFilesTest {
    @Test
    fun `ディレクトリが無ければ利用不可`(@TempDir tempDir: Path) {
        val target = tempDir.resolve("not-exist")
        val staticFiles = StaticFiles(target)

        assertFalse(staticFiles.isAvailable())
    }

    @Test
    fun `index html があれば解決できる`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertTrue(staticFiles.isAvailable())
        assertEquals(indexFile, staticFiles.resolve(listOf(), isHtmlRequest = false))
    }

    @Test
    fun `ページを開くときは無いファイルを index html に解決する`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertEquals(indexFile, staticFiles.resolve(listOf("admin", "dashboard"), isHtmlRequest = true))
        assertEquals(indexFile, staticFiles.resolve(listOf("@john.doe"), isHtmlRequest = true))
    }

    @Test
    fun `ページ以外の取得では無いファイルを index html に解決せず null`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertNull(staticFiles.resolve(listOf("assets", "bundle.js"), isHtmlRequest = false))
        assertNull(staticFiles.resolve(listOf("admin"), isHtmlRequest = false))
    }

    @Test
    fun `存在する静的ファイルは正しく解決される`(@TempDir tempDir: Path) {
        val assetsDir = tempDir.resolve("assets").apply { createDirectories() }
        val jsFile = assetsDir.resolve("app.js").apply { writeText("console.log('hi');") }

        val staticFiles = StaticFiles(tempDir)
        assertEquals(jsFile, staticFiles.resolve(listOf("assets", "app.js"), isHtmlRequest = false))
    }

    @Test
    fun `ディレクトリトラバーサルは null になる`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertNull(staticFiles.resolve(listOf("..", "secret.txt"), isHtmlRequest = true))
        assertNull(staticFiles.resolve(listOf("assets", "..", "..", "secret.txt"), isHtmlRequest = true))
    }

    @Test
    fun `内容のハッシュを含む JS と wasm の名前だけをハッシュ付きとみなす`() {
        assertTrue(StaticFiles.isContentHashedName("frontend.0123456789abcdef.js"))
        assertTrue(StaticFiles.isContentHashedName("0a5c250505a8cc7f74ca.wasm"))

        assertFalse(StaticFiles.isContentHashedName("frontend.js"))
        assertFalse(StaticFiles.isContentHashedName("app.wasm"))
        assertFalse(StaticFiles.isContentHashedName("index.html"))
        assertFalse(StaticFiles.isContentHashedName("0123456789abcdef.css"))
    }

    @Test
    fun `外を指すシンボリックリンクは配信しない`(@TempDir tempDir: Path) {
        val publicDir = tempDir.resolve("public").apply { createDirectories() }
        publicDir.resolve("index.html").writeText("<html></html>")
        val secret = tempDir.resolve("secret.txt").apply { writeText("secret") }
        Files.createSymbolicLink(publicDir.resolve("link.txt"), secret)
        Files.createSymbolicLink(publicDir.resolve("linked-dir"), tempDir)

        val staticFiles = StaticFiles(publicDir)
        assertNull(staticFiles.resolve(listOf("link.txt"), isHtmlRequest = false))
        assertNull(staticFiles.resolve(listOf("linked-dir", "secret.txt"), isHtmlRequest = false))
    }

    @Test
    fun `外を指す index html は返さない`(@TempDir tempDir: Path) {
        val publicDir = tempDir.resolve("public").apply { createDirectories() }
        val outside = tempDir.resolve("outside.html").apply { writeText("<html></html>") }
        Files.createSymbolicLink(publicDir.resolve("index.html"), outside)

        val staticFiles = StaticFiles(publicDir)
        assertNull(staticFiles.index())
        assertNull(staticFiles.resolve(listOf("admin"), isHtmlRequest = true))
    }
}
