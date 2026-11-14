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
import net.matsudamper.kmp.activitypub.logic.CustomEmojiService
import net.matsudamper.kmp.activitypub.logic.CustomEmojiUrls
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.activitypub.ActivityPubContentTypes
import net.matsudamper.kotpub.emoji.EmojiObject
import net.matsudamper.kotpub.json.AppJson
import org.slf4j.LoggerFactory

/**
 * このサーバーのカスタム絵文字の画像。他のサーバーはリアクションを受け取った後にここへ取りに来る
 */
internal class CustomEmojiFiles(
    private val customEmojiService: CustomEmojiService,
    private val imageStorage: ImageStorage,
) {
    private val logger = LoggerFactory.getLogger(CustomEmojiFiles::class.java)

    suspend fun find(publicId: String): CustomEmojiFile = withOpenTelemetryContext {
        val emoji = customEmojiService.findByPublicId(publicId) ?: return@withOpenTelemetryContext CustomEmojiFile.NotFound
        val request = ImageStorage.GetRequest(ownerAccountId = emoji.uploadedBy, path = ImagePath(emoji.path))
        when (val result = imageStorage.get(request)) {
            // 置き場の申告ではなく、受け付けたときに判定した形式で返す
            is ImageStorage.GetResult.Success -> CustomEmojiFile.Found(bytes = result.bytes, contentType = emoji.contentType)

            ImageStorage.GetResult.NotFound -> CustomEmojiFile.NotFound

            is ImageStorage.GetResult.Failure -> {
                logger.error("カスタム絵文字の画像を取れなかった: ${emoji.path}", result.cause)
                CustomEmojiFile.Unavailable
            }
        }
    }
}

internal sealed interface CustomEmojiFile {
    class Found(
        val bytes: ByteArray,
        val contentType: String,
    ) : CustomEmojiFile

    data object NotFound : CustomEmojiFile

    data object Unavailable : CustomEmojiFile
}

/**
 * 画像は差し替えると URL ごと変わるので、URL ごとに長く覚えさせてよい
 */
internal fun Route.customEmojiRoutes(
    files: CustomEmojiFiles,
    customEmojiService: CustomEmojiService,
    urls: CustomEmojiUrls,
) {
    get("${CustomEmojiUrls.IMAGE_PATH_PREFIX}/{publicId}") {
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
        when (val file = files.find(call.parameters["publicId"].orEmpty())) {
            is CustomEmojiFile.Found -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                call.respondBytes(bytes = file.bytes, contentType = ContentType.parse(file.contentType), status = HttpStatusCode.OK)
            }

            // 絵文字が消えれば画像も出さなくなるので、見つからないことは覚えさせない
            CustomEmojiFile.NotFound -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
            }

            CustomEmojiFile.Unavailable -> {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.ServiceUnavailable)
            }
        }
    }

    get("${CustomEmojiUrls.OBJECT_PATH_PREFIX}/{shortcode}") {
        val emoji = withOpenTelemetryContext { customEmojiService.findByShortcode(call.parameters["shortcode"].orEmpty()) }
        if (emoji == null) {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(bytes = byteArrayOf(), status = HttpStatusCode.NotFound)
        } else {
            val body = AppJson.encodeToString(EmojiObject.Document.serializer(), urls.emojiObjectOf(emoji).toDocument())
            call.respondBytes(
                bytes = body.encodeToByteArray(),
                contentType = ContentType.parse(ActivityPubContentTypes.ActivityJson.toString()),
                status = HttpStatusCode.OK,
            )
        }
    }
}

private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
