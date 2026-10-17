package net.matsudamper.kmp.activitypub.staticfiles

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import io.ktor.http.ContentType

class StaticFiles(
    srcDir: Path,
) {
    val root: Path = srcDir.toAbsolutePath().normalize()

    fun isAvailable(): Boolean = Files.isDirectory(root)

    fun index(): Path? = root.resolve(INDEX_FILE_NAME).takeIf { isServableFile(it) }

    fun resolve(segments: List<String>): Path? {
        val safeSegments = safeSegments(segments) ?: return null

        val requested = fileOf(safeSegments) ?: return null
        if (isServableFile(requested)) return requested

        if (safeSegments.lastOrNull()?.contains('.') == true && !isUserPagePath(safeSegments)) return null

        return index()
    }

    /**
     * 字句上の [root] 配下の確認だけでは、配下に置かれたシンボリックリンクから外のファイルを返してしまう。
     * リンクを解決した実体も root の実体の配下にあるものだけを返す
     */
    private fun isServableFile(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false

        return try {
            path.toRealPath().startsWith(root.toRealPath())
        } catch (_: IOException) {
            false
        }
    }

    private fun fileOf(safeSegments: List<String>): Path? {
        if (safeSegments.isEmpty()) return root.resolve(INDEX_FILE_NAME)

        val resolved =
            try {
                root.resolve(safeSegments.joinToString("/")).normalize()
            } catch (_: InvalidPathException) {
                return null
            }

        return resolved.takeIf { it.startsWith(root) }
    }

    /**
     * ユーザー名には `.` を使えるので、拡張子付きのファイル名と見分けられない
     */
    private fun isUserPagePath(safeSegments: List<String>): Boolean {
        return safeSegments.size == 1 && safeSegments.single().startsWith(USER_PAGE_PATH_PREFIX)
    }

    private fun safeSegments(segments: List<String>): List<String>? {
        val filtered = segments.filter { it.isNotEmpty() }

        val hasUnsafe =
            filtered.any { segment ->
                segment == "." || segment == ".." || segment.contains('/') || segment.contains('\\')
            }

        return filtered.takeIf { !hasUnsafe }
    }

    companion object {
        const val INDEX_FILE_NAME: String = "index.html"
        private const val USER_PAGE_PATH_PREFIX: String = "@"

        // 内容が変わると名前も変わるので、同じ名前のまま中身が変わらない。
        // 固定名のファイルに長期キャッシュを付けると、再デプロイ後も古いものが使われ続ける
        private val CONTENT_HASHED_NAME = Regex("""^(?:.+\.)?[0-9a-f]{16,}\.(?:js|wasm)$""")

        fun isContentHashedName(fileName: String): Boolean = CONTENT_HASHED_NAME.matches(fileName)

        fun contentTypeOf(fileName: String): ContentType =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "html" -> ContentType.Text.Html
                "js", "mjs" -> ContentType.Application.JavaScript
                "wasm" -> ContentType.Application.Wasm
                "css" -> ContentType.Text.CSS
                "json", "map" -> ContentType.Application.Json
                "svg" -> ContentType.Image.SVG
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "gif" -> ContentType.Image.GIF
                "ico" -> ContentType.Image.XIcon
                "webp" -> ContentType.Image.WEBP
                "woff2" -> ContentType.Font.Woff2
                "woff" -> ContentType.Font.Woff
                "ttf" -> ContentType.Font.Ttf
                "otf" -> ContentType.Font.Otf
                "txt" -> ContentType.Text.Plain
                "xml" -> ContentType.Application.Xml
                else -> ContentType.Application.OctetStream
            }
    }
}
