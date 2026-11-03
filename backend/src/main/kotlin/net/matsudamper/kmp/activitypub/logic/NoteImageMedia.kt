package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import org.slf4j.LoggerFactory

/**
 * [NoteImageUrls] の URL で配る中身を置き場から取る
 */
class NoteImageMedia(
    private val imageStorage: ImageStorage,
) {
    private val logger = LoggerFactory.getLogger(NoteImageMedia::class.java)

    fun find(
        accountId: String,
        fileId: String,
    ): NoteImageMediaResult {
        val location = NoteImagePaths.parse(accountId = accountId, fileId = fileId) ?: return NoteImageMediaResult.NotFound
        return when (val result = imageStorage.get(ImageStorage.GetRequest(ownerAccountId = location.accountId, path = location.path))) {
            is ImageStorage.GetResult.Success -> NoteImageMediaResult.Found(bytes = result.bytes, contentType = result.contentType)

            ImageStorage.GetResult.NotFound -> NoteImageMediaResult.NotFound

            is ImageStorage.GetResult.Failure -> {
                logger.error("投稿の画像を取れなかった: ${location.path.value}", result.cause)
                NoteImageMediaResult.Unavailable
            }
        }
    }
}

sealed interface NoteImageMediaResult {
    class Found(
        val bytes: ByteArray,
        val contentType: String,
    ) : NoteImageMediaResult

    data object NotFound : NoteImageMediaResult

    data object Unavailable : NoteImageMediaResult
}
