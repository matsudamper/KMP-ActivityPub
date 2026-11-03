package net.matsudamper.kmp.activitypub.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import net.matsudamper.kmp.activitypub.json.respondJson
import net.matsudamper.kmp.activitypub.logic.NoteImageMedia
import net.matsudamper.kmp.activitypub.logic.NoteImageMediaResult
import net.matsudamper.kmp.activitypub.logic.NoteImageService
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.UploadNoteImageResult
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext

internal const val NOTE_IMAGE_UPLOAD_PATH = "/api/note-images"

/**
 * 画像のバイト列をそのまま受ける。GraphQL に載せると base64 で膨らみ、ボディの上限に当たる
 *
 * Content-Type を画像に限るのは、プリフライト無しで別オリジンから送れる種類を断るため
 */
internal fun Route.noteImageUploadRoutes(
    service: NoteImageService,
    sessionService: UserSessionService,
    cookieSecure: Boolean,
) {
    post(NOTE_IMAGE_UPLOAD_PATH) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (UPLOADABLE_CONTENT_TYPES.none { call.request.contentType().match(it) }) {
            call.respondFailure(UploadFailure.UNSUPPORTED_IMAGE, HttpStatusCode.UnsupportedMediaType)
            return@post
        }
        val token = SessionCookieManager(call = call, secure = cookieSecure, cookieName = SessionCookieManager.USER_COOKIE_NAME).token()
        val accountId = withOpenTelemetryContext { token?.let(sessionService::accountIdOf) }
        if (accountId == null) {
            call.respondFailure(UploadFailure.NOT_LOGGED_IN, HttpStatusCode.Unauthorized)
            return@post
        }

        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > NoteImageService.MAX_BYTES) {
            call.respondFailure(UploadFailure.TOO_LARGE, HttpStatusCode.PayloadTooLarge)
            return@post
        }
        // chunked は上の判定をすり抜ける
        val image = call.receiveChannel().readBuffer(NoteImageService.MAX_BYTES + 1L).readByteArray()

        val result =
            withOpenTelemetryContext {
                withContext(Dispatchers.IO) {
                    service.upload(accountId = accountId, image = image, description = call.request.queryParameters["description"])
                }
            }
        when (result) {
            is UploadNoteImageResult.Uploaded -> call.respondJson(UploadResponse.serializer(), UploadResponse(id = result.id.value, failure = null))
            UploadNoteImageResult.TooLarge -> call.respondFailure(UploadFailure.TOO_LARGE, HttpStatusCode.PayloadTooLarge)
            UploadNoteImageResult.DimensionsTooLarge -> call.respondFailure(UploadFailure.DIMENSIONS_TOO_LARGE, HttpStatusCode.BadRequest)
            UploadNoteImageResult.DescriptionTooLong -> call.respondFailure(UploadFailure.DESCRIPTION_TOO_LONG, HttpStatusCode.BadRequest)
            UploadNoteImageResult.Unsupported -> call.respondFailure(UploadFailure.UNSUPPORTED_IMAGE, HttpStatusCode.UnsupportedMediaType)
            UploadNoteImageResult.UploadFailed -> call.respondFailure(UploadFailure.INTERNAL_SERVER_ERROR, HttpStatusCode.InternalServerError)
            UploadNoteImageResult.AccountNotFound -> call.respondFailure(UploadFailure.NOT_LOGGED_IN, HttpStatusCode.Unauthorized)
        }
    }
}

/**
 * Cloudflare に edge でキャッシュさせる。Set-Cookie や Vary を付けるとキャッシュされなくなるので付けない。
 * 場所は置くたびに変わり中身を差し替えないので、immutable で長く置かせる
 */
internal fun Route.noteImageMediaRoutes(media: NoteImageMedia) {
    get("${NoteImageUrls.PATH_PREFIX}/users/{accountId}/note-image/{fileId}") {
        val result =
            withOpenTelemetryContext {
                withContext(Dispatchers.IO) {
                    media.find(accountId = call.parameters["accountId"].orEmpty(), fileId = call.parameters["fileId"].orEmpty())
                }
            }
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
        when (result) {
            is NoteImageMediaResult.Found -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                call.respondBytes(bytes = result.bytes, contentType = ContentType.parse(result.contentType), status = HttpStatusCode.OK)
            }

            // 消した画像が edge に残らないよう、無いことも残さない
            NoteImageMediaResult.NotFound -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
            }

            NoteImageMediaResult.Unavailable -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.BadGateway)
            }
        }
    }
}

private suspend fun ApplicationCall.respondFailure(
    failure: UploadFailure,
    status: HttpStatusCode,
) {
    respondJson(UploadResponse.serializer(), UploadResponse(id = null, failure = failure), status = status)
}

@Serializable
private data class UploadResponse(
    val id: String?,
    val failure: UploadFailure?,
)

@Serializable
private enum class UploadFailure {
    NOT_LOGGED_IN,
    TOO_LARGE,
    DIMENSIONS_TOO_LARGE,
    DESCRIPTION_TOO_LONG,
    UNSUPPORTED_IMAGE,
    INTERNAL_SERVER_ERROR,
}

private val UPLOADABLE_CONTENT_TYPES =
    listOf(
        ContentType.Image.PNG,
        ContentType.Image.JPEG,
        ContentType.Image.GIF,
        ContentType("image", "webp"),
    )

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
