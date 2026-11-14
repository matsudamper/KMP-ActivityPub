package net.matsudamper.kmp.activitypub.logic

import java.io.InputStream
import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRepository
import net.matsudamper.kmp.activitypub.repository.LocalCustomEmoji
import net.matsudamper.kmp.activitypub.repository.NewLocalCustomEmoji
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId
import org.slf4j.LoggerFactory

/**
 * このサーバーのカスタム絵文字を置く・消す。サーバーの全員が使え、消せるのは置いたアカウントだけ
 *
 * @param imageStorage 置き場が無ければ置けない
 */
class CustomEmojiService(
    private val customEmojis: CustomEmojiRepository,
    private val imageStorage: ImageStorage?,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(CustomEmojiService::class.java)

    fun all(): List<LocalCustomEmoji> = customEmojis.all()

    /**
     * @param shortcode `:shortcode:` で入る名前。コロンを含めない
     */
    fun findByShortcode(shortcode: String): LocalCustomEmoji? = customEmojis.findByShortcode(shortcode)

    fun findByPublicId(publicId: String): LocalCustomEmoji? = customEmojis.findByPublicId(publicId)

    /**
     * @param shortcode コロンを含めない名前。英数字と `_` だけ
     */
    fun upload(accountId: AccountId, shortcode: String, image: CustomEmojiUpload): UploadCustomEmojiResult {
        if (!SHORTCODE_PATTERN.matches(shortcode)) return UploadCustomEmojiResult.InvalidShortcode
        if (image.size > MAX_IMAGE_BYTES) return UploadCustomEmojiResult.TooLarge
        val format = ImageFormat.detect(image.openStream().use { it.readNBytes(ImageFormat.SIGNATURE_BYTES) })
            ?: return UploadCustomEmojiResult.UnsupportedImage
        val storage = imageStorage ?: return UploadCustomEmojiResult.ImageStorageDisabled
        if (customEmojis.findByShortcode(shortcode) != null) return UploadCustomEmojiResult.DuplicateShortcode

        val path = CustomEmojiPaths.create(accountId)
        val result = image.openStream().use { inputStream ->
            storage.put(
                ImageStorage.PutRequest(
                    ownerAccountId = accountId,
                    path = path,
                    contentType = format.contentType,
                    maxBytes = MAX_IMAGE_BYTES.toLong(),
                    inputStream = inputStream,
                ),
            )
        }
        when (result) {
            is ImageStorage.PutResult.Success -> Unit

            ImageStorage.PutResult.PayloadTooLarge -> return UploadCustomEmojiResult.TooLarge

            ImageStorage.PutResult.Empty -> return UploadCustomEmojiResult.UnsupportedImage

            is ImageStorage.PutResult.Failure -> {
                logger.error("カスタム絵文字の画像を置けなかった: $accountId", result.cause)
                return UploadCustomEmojiResult.UploadFailed
            }
        }
        val added = customEmojis.add(
            NewLocalCustomEmoji(
                shortcode = shortcode,
                publicId = UUID.randomUUID().toString(),
                path = path.value,
                contentType = format.contentType,
                uploadedBy = accountId,
                createdAt = clock.instant(),
            ),
        )
        if (added == null) {
            // 確かめてから入れるまでの間に同じ名前が入った。置いた画像は使われないので消す
            storage.delete(ImageStorage.DeleteRequest(ownerAccountId = accountId, path = path))
            return UploadCustomEmojiResult.DuplicateShortcode
        }
        return UploadCustomEmojiResult.Uploaded(added)
    }

    fun delete(accountId: AccountId, id: CustomEmojiId): DeleteCustomEmojiResult {
        val storage = imageStorage ?: return DeleteCustomEmojiResult.ImageStorageDisabled
        val deleted = customEmojis.delete(id = id, accountId = accountId) ?: return DeleteCustomEmojiResult.NotFound
        val result = storage.delete(ImageStorage.DeleteRequest(ownerAccountId = accountId, path = ImagePath(deleted.path)))
        if (result is ImageStorage.DeleteResult.Failure) {
            // 絵文字は消えているので、画像が残っても誰からも引かれない
            logger.error("カスタム絵文字の画像を消せなかった: ${deleted.path}", result.cause)
        }
        return DeleteCustomEmojiResult.Deleted
    }

    private companion object {
        val SHORTCODE_PATTERN = Regex("[A-Za-z0-9_]{1,64}")
        const val MAX_IMAGE_BYTES = 1024 * 1024
    }
}

class CustomEmojiUpload(
    val size: Long,
    val openStream: () -> InputStream,
)

internal object CustomEmojiPaths {
    /**
     * 置き場の権限をアカウント単位に絞れるよう、置いたアカウントの下に置く。
     * 消すまで残す画像で、まとめて取っておくので日付で分けない
     */
    fun create(accountId: AccountId): ImagePath = ImagePath("users/${accountId.value}/custom-emojis/${UUID.randomUUID()}")
}

sealed interface UploadCustomEmojiResult {
    data class Uploaded(val emoji: LocalCustomEmoji) : UploadCustomEmojiResult

    data object InvalidShortcode : UploadCustomEmojiResult

    data object DuplicateShortcode : UploadCustomEmojiResult

    data object TooLarge : UploadCustomEmojiResult

    data object UnsupportedImage : UploadCustomEmojiResult

    data object ImageStorageDisabled : UploadCustomEmojiResult

    data object UploadFailed : UploadCustomEmojiResult
}

sealed interface DeleteCustomEmojiResult {
    data object Deleted : DeleteCustomEmojiResult

    /**
     * 無いか、置いたアカウントでない
     */
    data object NotFound : DeleteCustomEmojiResult

    data object ImageStorageDisabled : DeleteCustomEmojiResult
}
