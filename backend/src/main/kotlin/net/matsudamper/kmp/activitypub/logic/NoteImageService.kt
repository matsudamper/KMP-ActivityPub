package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.NewNoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImageId
import net.matsudamper.kmp.activitypub.repository.NoteImageRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.slf4j.LoggerFactory

/**
 * 投稿に添える画像を先に置く。投稿に添えるのは [NotePostService]
 */
class NoteImageService(
    private val imageStorage: ImageStorage,
    private val noteImageRepository: NoteImageRepository,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(NoteImageService::class.java)

    /**
     * @param description 空なら代替テキスト無し
     */
    fun upload(
        accountId: AccountId,
        image: ByteArray,
        description: String?,
    ): UploadNoteImageResult {
        if (image.size > MAX_BYTES) return UploadNoteImageResult.TooLarge
        val normalizedDescription = description?.trim()?.ifEmpty { null }
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (normalizedDescription != null && normalizedDescription.codePointCount(0, normalizedDescription.length) > MAX_DESCRIPTION_LENGTH) {
            return UploadNoteImageResult.DescriptionTooLong
        }
        val format = ProfileIconFormat.detect(image) ?: return UploadNoteImageResult.Unsupported
        val dimensions = ProfileIconDimensions.read(image, format) ?: return UploadNoteImageResult.Unsupported
        if (dimensions.width > MAX_SIDE_PX || dimensions.height > MAX_SIDE_PX) {
            return UploadNoteImageResult.DimensionsTooLarge
        }

        val path = NoteImagePaths.create(accountId)
        val putResult =
            imageStorage.put(
                ImageStorage.PutRequest(
                    ownerAccountId = accountId,
                    path = path,
                    contentType = format.contentType,
                    maxBytes = MAX_BYTES.toLong(),
                    inputStream = image.inputStream(),
                ),
            )
        when (putResult) {
            is ImageStorage.PutResult.Success -> Unit

            ImageStorage.PutResult.PayloadTooLarge -> return UploadNoteImageResult.TooLarge

            ImageStorage.PutResult.Empty -> return UploadNoteImageResult.Unsupported

            is ImageStorage.PutResult.Failure -> {
                logger.error("投稿の画像を置けなかった: $accountId", putResult.cause)
                return UploadNoteImageResult.UploadFailed
            }
        }

        val id = NoteImageId(UUID.randomUUID().toString())
        val added =
            noteImageRepository.add(
                NewNoteImage(
                    accountId = accountId,
                    id = id,
                    path = path.value,
                    mediaType = format.contentType,
                    description = normalizedDescription,
                    width = dimensions.width,
                    height = dimensions.height,
                    uploadedAt = clock.instant(),
                ),
            )
        if (!added) {
            imageStorage.delete(ImageStorage.DeleteRequest(ownerAccountId = accountId, path = path))
            return UploadNoteImageResult.AccountNotFound
        }
        return UploadNoteImageResult.Uploaded(id)
    }

    companion object {
        /**
         * 画面はロスレスの WebP で送るので、写真は大きくなる
         */
        const val MAX_BYTES: Int = 8 * 1024 * 1024

        /**
         * 縦横それぞれの上限。画面は送る前にもっと小さく縮める
         */
        const val MAX_SIDE_PX: Int = 4096

        /**
         * Mastodon の代替テキストの上限
         */
        const val MAX_DESCRIPTION_LENGTH: Int = 1500
    }
}

/**
 * 画像の置き場の中での投稿の画像の場所
 */
object NoteImagePaths {
    private const val KIND = "note-image"

    /**
     * 置き場の権限をアカウント単位に絞れるよう、`users/{アカウントの id}/` の下に置く
     */
    fun create(accountId: AccountId): ImagePath = ImagePath("users/${accountId.value}/$KIND/${UUID.randomUUID()}")

    /**
     * 配信の URL から場所を組み立てる。投稿の画像の形でなければ null
     */
    fun parse(
        accountId: String,
        fileId: String,
    ): NoteImageLocation? {
        val parsedAccountId = accountId.toLongOrNull()?.let(::AccountId) ?: return null
        val parsedFileId = runCatching { UUID.fromString(fileId) }.getOrNull() ?: return null
        // UUID.fromString は桁の足りない綴りも通すので、正規の綴りと同じものだけ受ける
        if (parsedFileId.toString() != fileId) return null
        return NoteImageLocation(
            accountId = parsedAccountId,
            path = ImagePath("users/${parsedAccountId.value}/$KIND/$parsedFileId"),
        )
    }
}

data class NoteImageLocation(
    val accountId: AccountId,
    val path: ImagePath,
)

/**
 * 投稿の画像を配る URL。中身は差し替えないので、edge に長く置かせる
 */
class NoteImageUrls(
    private val domain: String,
) {
    fun url(path: String): String = "https://$domain$PATH_PREFIX/$path"

    companion object {
        const val PATH_PREFIX: String = "/media"
    }
}

sealed interface UploadNoteImageResult {
    data class Uploaded(val id: NoteImageId) : UploadNoteImageResult

    data object TooLarge : UploadNoteImageResult

    data object DimensionsTooLarge : UploadNoteImageResult

    data object DescriptionTooLong : UploadNoteImageResult

    /**
     * PNG / JPEG / GIF / WebP ではないか、寸法を読めない
     */
    data object Unsupported : UploadNoteImageResult

    data object UploadFailed : UploadNoteImageResult

    data object AccountNotFound : UploadNoteImageResult
}
