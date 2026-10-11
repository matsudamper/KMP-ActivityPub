package net.matsudamper.kmp.activitypub.staticfiles

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
        assertEquals(indexFile, staticFiles.resolve(listOf()))
    }

    @Test
    fun `SPA の未知のパスは index html に解決される`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertEquals(indexFile, staticFiles.resolve(listOf("admin", "dashboard")))
    }

    @Test
    fun `拡張子付きの存在しないファイルは index html に解決されず null`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertNull(staticFiles.resolve(listOf("assets", "bundle.js")))
    }

    @Test
    fun `存在する静的ファイルは正しく解決される`(@TempDir tempDir: Path) {
        val assetsDir = tempDir.resolve("assets").apply { createDirectories() }
        val jsFile = assetsDir.resolve("app.js").apply { writeText("console.log('hi');") }

        val staticFiles = StaticFiles(tempDir)
        assertEquals(jsFile, staticFiles.resolve(listOf("assets", "app.js")))
    }

    @Test
    fun `ディレクトリトラバーサルは null になる`(@TempDir tempDir: Path) {
        val indexFile = tempDir.resolve("index.html")
        indexFile.writeText("<html></html>")

        val staticFiles = StaticFiles(tempDir)
        assertNull(staticFiles.resolve(listOf("..", "secret.txt")))
        assertNull(staticFiles.resolve(listOf("assets", "..", "..", "secret.txt")))
    }
}
