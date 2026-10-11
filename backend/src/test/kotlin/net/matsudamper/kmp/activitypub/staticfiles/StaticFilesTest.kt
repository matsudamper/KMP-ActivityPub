package net.matsudamper.kmp.activitypub.staticfiles

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.http.ContentType

class StaticFilesTest {
    /** 配信ディレクトリの外にファイルを置きたいので、1 段上を作っておく */
    private val base: Path = Files.createTempDirectory("static-files-test")
    private val root: Path = base.resolve("dist").also { it.createDirectories() }

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun `rootはindex_htmlを返す`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(emptyList()))
    }

    @Test
    fun `ファイルがあればそれを返す`() {
        putFile("index.html", "<html></html>")
        val script = putFile("frontend.js", "console.log()")

        assertEquals(script, staticFiles().resolve(listOf("frontend.js")))
    }

    @Test
    fun `サブディレクトリのファイルも返す`() {
        val font = putFile("fonts/main.woff2", "font")

        assertEquals(font, staticFiles().resolve(listOf("fonts", "main.woff2")))
    }

    @Test
    fun `拡張子の無いパスは画面のパスとしてindex_htmlを返す`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(listOf("admin", "password-hash")))
    }

    @Test
    fun `拡張子のあるパスはindex_htmlに落とさず見つからないとする`() {
        putFile("index.html", "<html></html>")

        assertNull(staticFiles().resolve(listOf("missing.js")))
    }

    @Test
    fun `index_htmlが無ければ画面のパスも見つからないとする`() {
        putFile("frontend.js", "console.log()")

        assertNull(staticFiles().resolve(listOf("admin")))
    }

    @Test
    fun `親ディレクトリを辿るパスは読ませない`() {
        putFile("index.html", "<html></html>")
        Files.writeString(base.resolve("secret.txt"), "secret")

        assertNull(staticFiles().resolve(listOf("..", "secret.txt")))
    }

    @Test
    fun `途中に親ディレクトリを挟んでも読ませない`() {
        putFile("index.html", "<html></html>")
        Files.writeString(base.resolve("secret.txt"), "secret")

        assertNull(staticFiles().resolve(listOf("fonts", "..", "..", "secret.txt")))
    }

    @Test
    fun `空のセグメントは無視してファイルを引く`() {
        val script = putFile("frontend.js", "console.log()")

        assertEquals(script, staticFiles().resolve(listOf("", "frontend.js", "")))
    }

    @Test
    fun `ディレクトリそのものは返さない`() {
        val index = putFile("index.html", "<html></html>")
        putFile("fonts/main.woff2", "font")

        // 拡張子が無いので画面のパスと同じ扱いになる
        assertEquals(index, staticFiles().resolve(listOf("fonts")))
    }

    @Test
    fun `ディレクトリが無ければ配信できないとする`() {
        assertEquals(false, StaticFiles(root.resolve("not-exists")).isAvailable())
    }

    @Test
    fun `wasmはapplication_wasmで返す`() {
        assertEquals(ContentType.Application.Wasm, StaticFiles.contentTypeOf("frontend.wasm"))
    }

    @Test
    fun `拡張子から Content-Type を決める`() {
        assertEquals(ContentType.Text.Html, StaticFiles.contentTypeOf("index.html"))
        assertEquals(ContentType.Application.JavaScript, StaticFiles.contentTypeOf("frontend.js"))
        assertEquals(ContentType.Font.Woff2, StaticFiles.contentTypeOf("main.WOFF2"))
        assertEquals(ContentType.Application.OctetStream, StaticFiles.contentTypeOf("unknown"))
    }

    private fun staticFiles(): StaticFiles = StaticFiles(root)

    @Test
    fun `外を指すシンボリックリンクは配信しない`() {
        putFile("index.html", "<html></html>")
        val secret = base.resolve("secret.txt").apply { writeText("secret") }
        Files.createSymbolicLink(root.resolve("link.txt"), secret)
        Files.createSymbolicLink(root.resolve("linked-dir"), base)

        assertNull(staticFiles().resolve(listOf("link.txt")))
        assertNull(staticFiles().resolve(listOf("linked-dir", "secret.txt")))
    }

    @Test
    fun `外を指す index html は返さない`() {
        val outside = base.resolve("outside.html").apply { writeText("<html></html>") }
        Files.createSymbolicLink(root.resolve("index.html"), outside)

        assertNull(staticFiles().index())
        assertNull(staticFiles().resolve(listOf("admin")))
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

    private fun putFile(
        relativePath: String,
        content: String,
    ): Path {
        val path = root.resolve(relativePath)
        path.parent.createDirectories()
        path.writeText(content)
        return path.toAbsolutePath().normalize()
    }
}
