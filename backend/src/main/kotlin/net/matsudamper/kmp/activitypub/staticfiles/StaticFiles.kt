package net.matsudamper.kmp.activitypub.staticfiles

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import io.ktor.http.ContentType

class StaticFiles(
    srcDir: Path,
) {
    val root: Path = srcDir.toAbsolutePath().normalize()

    fun isAvailable(): Boolean = Files.isDirectory(root)

    fun index(): Path? = root.resolve(INDEX_FILE_NAME).takeIf { Files.isRegularFile(it) }

    fun resolve(segments: List<String>): Path? {
        val safeSegments = safeSegments(segments) ?: return null

        val requested = fileOf(safeSegments) ?: return null
        if (Files.isRegularFile(requested)) return requested

        if (safeSegments.lastOrNull()?.contains('.') == true) return null

        return index()
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
