package net.matsudamper.kmp.activitypub.staticfiles

import kotlin.io.path.name
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val NO_STATIC_FILES_MESSAGE: String = "静的ファイルの配信先が無い。STATIC_SRC_DIR を確認すること"
private const val INDEX_CACHE_CONTROL: String = "no-store"

internal fun Route.staticRoutes(staticFiles: StaticFiles?) {
    get("/{path...}") {
        if (staticFiles == null) {
            call.respondText(NO_STATIC_FILES_MESSAGE, status = HttpStatusCode.NotFound)
            return@get
        }

        val segments = call.parameters.getAll("path").orEmpty()

        val file = staticFiles.resolve(segments)
        if (file == null) {
            call.respondText("見つからない: /${segments.joinToString("/")}", status = HttpStatusCode.NotFound)
            return@get
        }

        when {
            file.fileName.name == StaticFiles.INDEX_FILE_NAME -> {
                call.response.header(HttpHeaders.CacheControl, INDEX_CACHE_CONTROL)
            }

            StaticFiles.isContentHashedName(file.fileName.name) -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            }
        }

        call.respond(LocalFileContent(file.toFile(), StaticFiles.contentTypeOf(file.fileName.toString())))
    }
}
