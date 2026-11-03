package net.matsudamper.kmp.activitypub.federation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import org.slf4j.LoggerFactory

/**
 * 投稿に付けた画像を返す。他のサーバーは投稿を受け取った後にここへ取りに来る
 */
internal class NoteImageFiles(
    private val noteRepository: NoteRepository,
    private val imageStorage: ImageStorage,
) {
    private val logger = LoggerFactory.getLogger(NoteImageFiles::class.java)

    suspend fun find(publicId: String): NoteImageFile = withOpenTelemetryContext {
        val stored = noteRepository.findImage(NoteImagePublicId(publicId)) ?: return@withOpenTelemetryContext NoteImageFile.NotFound
        val request = ImageStorage.GetRequest(ownerAccountId = stored.ownerAccountId, path = ImagePath(stored.image.path))
        when (val result = imageStorage.get(request)) {
            // 置き場の申告ではなく、受け付けたときに判定した形式で返す
            is ImageStorage.GetResult.Success -> NoteImageFile.Found(bytes = result.bytes, contentType = stored.image.contentType)

            ImageStorage.GetResult.NotFound -> NoteImageFile.NotFound

            is ImageStorage.GetResult.Failure -> {
                logger.error("投稿の画像を取れなかった: ${stored.image.path}", result.cause)
                NoteImageFile.Unavailable
            }
        }
    }
}

internal sealed interface NoteImageFile {
    class Found(
        val bytes: ByteArray,
        val contentType: String,
    ) : NoteImageFile

    data object NotFound : NoteImageFile

    data object Unavailable : NoteImageFile
}

/**
 * 画像は差し替えないので、URL ごとに長く覚えさせてよい
 */
internal fun Route.noteImageRoutes(files: NoteImageFiles) {
    get("${NoteImageUrls.PATH_PREFIX}/{publicId}") {
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
        when (val file = files.find(call.parameters["publicId"].orEmpty())) {
            is NoteImageFile.Found -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                call.respondBytes(bytes = file.bytes, contentType = ContentType.parse(file.contentType), status = HttpStatusCode.OK)
            }

            // 投稿が消えれば画像も出さなくなるので、見つからないことは覚えさせない
            NoteImageFile.NotFound -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
            }

            NoteImageFile.Unavailable -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.ServiceUnavailable)
            }
        }
    }
}

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
