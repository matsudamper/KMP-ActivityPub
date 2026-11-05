package net.matsudamper.kmp.activitypub.logic

import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.util.UUID
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.UploadedNoteImage
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteAttachment
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積む。送るのは配信キューのワーカー。
 * 画像は先に [uploadImage] で置き、[post] には置いた画像の id を渡す
 *
 * @param imageStorage null なら画像を置けないので、画像付きの投稿はできない
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val accountService: AccountService,
    private val noteRepository: NoteRepository,
    private val imageStorage: ImageStorage?,
    private val imageUrls: NoteImageUrls,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
    private val onPosted: (PublicNoteId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(NotePostService::class.java)

    /**
     * 画像を置き場に置く。[UPLOADED_IMAGE_LIFETIME] のうちに投稿に付けなければ [UploadedNoteImageCleaner] が消す
     */
    fun uploadImage(
        username: String,
        image: NoteImageUpload,
    ): UploadNoteImageResult {
        if (image.size > MAX_IMAGE_BYTES) return UploadNoteImageResult.TooLarge
        val format = ImageFormat.detect(image.openStream().use { it.readNBytes(ImageFormat.SIGNATURE_BYTES) }) ?: return UploadNoteImageResult.UnsupportedImage
        val description = image.description?.trim()?.ifEmpty { null }
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (description != null && description.codePointCount(0, description.length) > MAX_DESCRIPTION_LENGTH) {
            return UploadNoteImageResult.DescriptionTooLong
        }
        val account = accountService.account(username) ?: return UploadNoteImageResult.AccountNotFound
        val storage = imageStorage ?: return UploadNoteImageResult.ImageStorageDisabled

        val path = NoteImagePaths.create(account.id)
        val result =
            image.openStream().use { inputStream ->
                storage.put(
                    ImageStorage.PutRequest(
                        ownerAccountId = account.id,
                        path = path,
                        contentType = format.contentType,
                        maxBytes = MAX_IMAGE_BYTES.toLong(),
                        inputStream = inputStream,
                    ),
                )
            }
        when (result) {
            is ImageStorage.PutResult.Success -> Unit

            ImageStorage.PutResult.PayloadTooLarge -> return UploadNoteImageResult.TooLarge

            ImageStorage.PutResult.Empty -> return UploadNoteImageResult.UnsupportedImage

            is ImageStorage.PutResult.Failure -> {
                logger.error("投稿の画像を置けなかった: ${account.id}", result.cause)
                return UploadNoteImageResult.UploadFailed
            }
        }
        val noteImage =
            NoteImage(
                publicId = NoteImagePublicId(UUID.randomUUID().toString()),
                path = path.value,
                contentType = format.contentType,
                description = description,
            )
        noteRepository.addUploadedImage(
            UploadedNoteImage(ownerAccountId = account.id, image = noteImage, expiresAt = clock.instant().plus(UPLOADED_IMAGE_LIFETIME)),
        )
        return UploadNoteImageResult.Uploaded(noteImage.publicId)
    }

    /**
     * 画像があれば本文は空でもよい
     *
     * @param requestId 画面が送信ごとに作る ID。同じ ID で送り直されたら、新しく作らずに前の投稿を返す
     * @param imageIds [uploadImage] で置いた画像。付けた順に並ぶ
     */
    fun post(
        username: String,
        body: String,
        requestId: String,
        imageIds: List<NoteImagePublicId>,
    ): PostNoteResult {
        if (imageIds.size > MAX_IMAGES) return PostNoteResult.TooManyImages
        val contentHtml =
            when (val composed = NoteComposer.compose(body)) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (imageIds.isEmpty()) return PostNoteResult.EmptyBody else ""
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }
        val sender = directory.resolve(username) ?: return PostNoteResult.AccountNotFound
        val account = accountService.account(sender.username) ?: return PostNoteResult.AccountNotFound
        // 送り直しで付ける画像は最初の投稿に付いて期限の記録から消えているので、画像を探す前に見分ける
        val alreadyPostedPublicId = noteRepository.findPublicIdByRequestId(username = sender.username, requestId = requestId)
        if (alreadyPostedPublicId != null) {
            logger.info("送り直された投稿なので新しく作らない: ${sender.acct} ${alreadyPostedPublicId.value}")
            return PostNoteResult.Posted(publicId = alreadyPostedPublicId, url = noteUrl(sender.domain, alreadyPostedPublicId))
        }
        if (imageIds.distinct().size != imageIds.size) return PostNoteResult.ImageNotFound
        val images = noteRepository.findUploadedImages(ownerAccountId = account.id, publicIds = imageIds, now = clock.instant())
        if (images.size != imageIds.size) return PostNoteResult.ImageNotFound

        val prepared =
            publisher.prepare(
                sender = sender,
                contentHtml = contentHtml,
                attachments =
                images.map { image ->
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
                    images = images,
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
                PostNoteResult.Posted(publicId = enqueued.publicId, url = noteUrl(sender.domain, enqueued.publicId))
            }

            EnqueueNoteResult.AccountNotFound -> PostNoteResult.AccountNotFound

            EnqueueNoteResult.ImageNotFound -> PostNoteResult.ImageNotFound
        }
    }

    private fun noteUrl(
        domain: String,
        publicId: PublicNoteId,
    ): String = NoteUrls(domain, KotpubPublicNoteId(publicId.value)).noteUrl

    companion object {
        /**
         * Mastodon の 1 投稿あたりの画像の上限
         */
        const val MAX_IMAGES: Int = 4

        /**
         * 写真をそのまま送れる大きさにする。GraphQL の multipart の上限はこれに合わせている
         */
        const val MAX_IMAGE_BYTES: Int = 20 * 1024 * 1024

        /**
         * Mastodon の画像の説明文の上限
         */
        const val MAX_DESCRIPTION_LENGTH: Int = 1500

        /**
         * 置いてから投稿に付けるまでの猶予。画面は投稿する直前に置くので、送り直しを含めても足りる
         */
        val UPLOADED_IMAGE_LIFETIME: Duration = Duration.ofHours(1)
    }
}

/**
 * @param size 送られてきた画像のバイト数
 * @param openStream 送られてきた画像を読む。形式はここでは信じず、先頭のバイト列で決める。開くたびに先頭から読める
 * @param description 画像の説明文。無ければ null
 */
class NoteImageUpload(
    val size: Long,
    val openStream: () -> InputStream,
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

sealed interface UploadNoteImageResult {
    data class Uploaded(
        val publicId: NoteImagePublicId,
    ) : UploadNoteImageResult

    data object TooLarge : UploadNoteImageResult

    /**
     * PNG / JPEG / GIF / WebP ではない
     */
    data object UnsupportedImage : UploadNoteImageResult

    data object DescriptionTooLong : UploadNoteImageResult

    data object ImageStorageDisabled : UploadNoteImageResult

    data object UploadFailed : UploadNoteImageResult

    data object AccountNotFound : UploadNoteImageResult
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

    /**
     * 置いていないか、期限を過ぎたか、もう別の投稿に付けた画像がある
     */
    data object ImageNotFound : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}
