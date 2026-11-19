package net.matsudamper.kmp.activitypub.staticfiles

import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.acceptItems
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.logic.PageOgpService
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext

private const val NO_STATIC_FILES_MESSAGE: String = "静的ファイルの配信先が無い。STATIC_SRC_DIR を確認すること"
private const val INDEX_CACHE_CONTROL: String = "no-store"

/**
 * @param pageOgpService 他のサーバーとやりとりしないサーバーでは null。カードの meta を足さない
 */
internal fun Route.staticRoutes(staticFiles: StaticFiles?, pageOgpService: PageOgpService?) {
    get("/{path...}") {
        if (staticFiles == null) {
            call.respondText(NO_STATIC_FILES_MESSAGE, status = HttpStatusCode.NotFound)
            return@get
        }

        val segments = call.parameters.getAll("path").orEmpty()

        val isHtmlRequest = call.request.acceptItems().any { it.value == ContentType.Text.Html.toString() }
        val file = staticFiles.resolve(segments = segments, isHtmlRequest = isHtmlRequest)
        if (file == null) {
            call.respondText("見つからない: /${segments.joinToString("/")}", status = HttpStatusCode.NotFound)
            return@get
        }

        val isIndex = file.fileName.name == StaticFiles.INDEX_FILE_NAME
        val ogp = if (isIndex && isHtmlRequest && pageOgpService != null) {
            withOpenTelemetryContext { pageOgpService.find(segments) }
        } else {
            null
        }
        if (ogp != null) {
            call.response.header(HttpHeaders.CacheControl, INDEX_CACHE_CONTROL)
            val indexHtml = withContext(Dispatchers.IO) { file.readText() }
            call.respondText(PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp), ContentType.Text.Html.withCharset(Charsets.UTF_8))
            return@get
        }

        when {
            isIndex -> {
                call.response.header(HttpHeaders.CacheControl, INDEX_CACHE_CONTROL)
            }

            StaticFiles.isContentHashedName(file.fileName.name) -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            }
        }

        call.respond(LocalFileContent(file.toFile(), StaticFiles.contentTypeOf(file.fileName.toString())))
    }
}
