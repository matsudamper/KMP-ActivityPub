package net.matsudamper.kmp.activitypub.logic

import java.util.UUID
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImagePublicId
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteAttachment
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積む。送るのは配信キューのワーカー
 *
 * @param imageStorage null なら画像を置けないので、画像付きの投稿はできない
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val accountService: AccountService,
    private val imageStorage: ImageStorage?,
    private val imageUrls: NoteImageUrls,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val onEnqueued: () -> Unit,
    private val onPosted: (PublicNoteId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(NotePostService::class.java)

    /**
     * 画像があれば本文は空でもよい
     *
     * @param requestId 画面が送信ごとに作る ID。同じ ID で送り直されたら、新しく作らずに前の投稿を返す
     * @param images 付ける画像。付けた順に並ぶ
     */
    fun post(
        username: String,
        body: String,
        requestId: String,
        images: List<NoteImageUpload>,
    ): PostNoteResult {
        if (images.size > MAX_IMAGES) return PostNoteResult.TooManyImages
        val contentHtml =
            when (val composed = NoteComposer.compose(body)) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (images.isEmpty()) return PostNoteResult.EmptyBody else ""
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }
        val checkedImages =
            images.map { image ->
                when (val checked = check(image)) {
                    is CheckImageResult.Accepted -> checked.image
                    is CheckImageResult.Rejected -> return checked.result
                }
            }
        val sender = directory.resolve(username) ?: return PostNoteResult.AccountNotFound
        val account = accountService.account(sender.username) ?: return PostNoteResult.AccountNotFound

        val storedImages =
            when (val put = putImages(accountId = account.id, images = checkedImages)) {
                is PutImagesResult.Success -> put.images
                is PutImagesResult.Failure -> return put.result
            }
        val prepared =
            publisher.prepare(
                sender = sender,
                contentHtml = contentHtml,
                attachments =
                storedImages.map { image ->
                    NoteAttachment(url = imageUrls.url(image.publicId), mediaType = image.contentType, description = image.description)
                },
            )
        val enqueued =
            deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = sender.username,
                    requestId = requestId,
                    publicId = PublicNoteId(prepared.publicId.value),
                    contentHtml = prepared.contentHtml,
                    publishedAt = prepared.publishedAt,
                    images = storedImages,
                    body = prepared.activityJson,
                    inboxes = followerRepository.findDeliveryInboxes(sender.username),
                ),
            )
        return when (enqueued) {
            is EnqueueNoteResult.Enqueued -> {
                logger.info("投稿を積んだ: ${sender.acct} ${prepared.publicId.value} 宛先=${enqueued.deliveries}")
                onEnqueued()
                onPosted(PublicNoteId(prepared.publicId.value))
                PostNoteResult.Posted(publicId = PublicNoteId(prepared.publicId.value), url = prepared.url)
            }

            is EnqueueNoteResult.AlreadyPosted -> {
                logger.info("送り直された投稿なので新しく作らない: ${sender.acct} ${enqueued.publicId.value}")
                deleteImages(accountId = account.id, images = storedImages)
                PostNoteResult.Posted(
                    publicId = enqueued.publicId,
                    url = NoteUrls(sender.domain, KotpubPublicNoteId(enqueued.publicId.value)).noteUrl,
                )
            }

            EnqueueNoteResult.AccountNotFound -> {
                deleteImages(accountId = account.id, images = storedImages)
                PostNoteResult.AccountNotFound
            }
        }
    }

    private fun check(image: NoteImageUpload): CheckImageResult {
        if (image.bytes.size > MAX_IMAGE_BYTES) return CheckImageResult.Rejected(PostNoteResult.ImageTooLarge)
        val format = ImageFormat.detect(image.bytes) ?: return CheckImageResult.Rejected(PostNoteResult.UnsupportedImage)
        val description = image.description?.trim()?.ifEmpty { null }
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (description != null && description.codePointCount(0, description.length) > MAX_DESCRIPTION_LENGTH) {
            return CheckImageResult.Rejected(PostNoteResult.DescriptionTooLong)
        }
        return CheckImageResult.Accepted(CheckedImage(bytes = image.bytes, format = format, description = description))
    }

    /**
     * 1 枚でも置けなければ、置いた分を消して投稿をやめる
     */
    private fun putImages(
        accountId: AccountId,
        images: List<CheckedImage>,
    ): PutImagesResult {
        if (images.isEmpty()) return PutImagesResult.Success(listOf())
        val storage = imageStorage ?: return PutImagesResult.Failure(PostNoteResult.ImageStorageDisabled)

        val stored = mutableListOf<NoteImage>()
        images.forEach { image ->
            val path = NoteImagePaths.create(accountId)
            val result =
                storage.put(
                    ImageStorage.PutRequest(
                        ownerAccountId = accountId,
                        path = path,
                        contentType = image.format.contentType,
                        maxBytes = MAX_IMAGE_BYTES.toLong(),
                        inputStream = image.bytes.inputStream(),
                    ),
                )
            val failure =
                when (result) {
                    is ImageStorage.PutResult.Success -> null

                    ImageStorage.PutResult.PayloadTooLarge -> PostNoteResult.ImageTooLarge

                    ImageStorage.PutResult.Empty -> PostNoteResult.UnsupportedImage

                    is ImageStorage.PutResult.Failure -> {
                        logger.error("投稿の画像を置けなかった: $accountId", result.cause)
                        PostNoteResult.UploadFailed
                    }
                }
            if (failure != null) {
                deleteImages(accountId = accountId, images = stored)
                return PutImagesResult.Failure(failure)
            }
            stored +=
                NoteImage(
                    publicId = NoteImagePublicId(UUID.randomUUID().toString()),
                    path = path.value,
                    contentType = image.format.contentType,
                    description = image.description,
                )
        }
        return PutImagesResult.Success(stored)
    }

    /**
     * 消せなくても使われない画像が残るだけなので、投稿の結果は変えない
     */
    private fun deleteImages(
        accountId: AccountId,
        images: List<NoteImage>,
    ) {
        val storage = imageStorage ?: return
        images.forEach { image ->
            val result = storage.delete(ImageStorage.DeleteRequest(ownerAccountId = accountId, path = ImagePath(image.path)))
            if (result is ImageStorage.DeleteResult.Failure) {
                logger.warn("使わなくなった投稿の画像を消せなかった: ${image.path}", result.cause)
            }
        }
    }

    private class CheckedImage(
        val bytes: ByteArray,
        val format: ImageFormat,
        val description: String?,
    )

    private sealed interface CheckImageResult {
        class Accepted(val image: CheckedImage) : CheckImageResult

        class Rejected(val result: PostNoteResult) : CheckImageResult
    }

    private sealed interface PutImagesResult {
        class Success(val images: List<NoteImage>) : PutImagesResult

        class Failure(val result: PostNoteResult) : PutImagesResult
    }

    companion object {
        /**
         * Mastodon の 1 投稿あたりの画像の上限
         */
        const val MAX_IMAGES: Int = 4

        /**
         * 画面は送る前に縮めるので、縮めた写真が収まる大きさにする。GraphQL の本文の上限はこれに合わせている
         */
        const val MAX_IMAGE_BYTES: Int = 2 * 1024 * 1024

        /**
         * Mastodon の画像の説明文の上限
         */
        const val MAX_DESCRIPTION_LENGTH: Int = 1500
    }
}

/**
 * @param bytes 送られてきた画像。形式はここでは信じず、先頭のバイト列で決める
 * @param description 画像の説明文。無ければ null
 */
class NoteImageUpload(
    val bytes: ByteArray,
    val description: String?,
)

/**
 * 画像の置き場の中での投稿の画像の場所
 */
internal object NoteImagePaths {
    /**
     * 置き場の権限をアカウント単位に絞れるよう、`users/{アカウントの id}/` の下に置く
     */
    fun create(accountId: AccountId): ImagePath = ImagePath("users/${accountId.value}/note-images/${UUID.randomUUID()}")
}

sealed interface PostNoteResult {
    data class Posted(
        val publicId: PublicNoteId,
        val url: String,
    ) : PostNoteResult

    /**
     * 本文も画像も無い
     */
    data object EmptyBody : PostNoteResult

    data object TooLong : PostNoteResult

    data object TooManyImages : PostNoteResult

    data object ImageTooLarge : PostNoteResult

    /**
     * PNG / JPEG / GIF / WebP ではない
     */
    data object UnsupportedImage : PostNoteResult

    data object DescriptionTooLong : PostNoteResult

    data object ImageStorageDisabled : PostNoteResult

    data object UploadFailed : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}
