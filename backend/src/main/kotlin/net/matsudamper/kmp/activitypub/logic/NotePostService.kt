package net.matsudamper.kmp.activitypub.logic

import java.io.InputStream
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NoteDeletion
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteMention
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.ReplyTarget
import net.matsudamper.kmp.activitypub.repository.UploadedNoteImage
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteAttachment
import net.matsudamper.kotpub.note.NoteMention as KotpubNoteMention
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積む。消すときも同じく Delete を積む。送るのは配信キューのワーカー。
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

        val path = NoteImagePaths.create(accountId = account.id, uploadedAt = clock.instant())
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
     * @param replyToPostId 返信するタイムラインの投稿の id。投稿者に宛てて、投稿者の inbox にも積む。返信でなければ null
     */
    fun post(
        username: String,
        body: String,
        requestId: String,
        imageIds: List<NoteImagePublicId>,
        replyToPostId: String?,
    ): PostNoteResult {
        if (imageIds.size > MAX_IMAGES) return PostNoteResult.TooManyImages
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
        val reply =
            if (replyToPostId == null) {
                null
            } else {
                resolveReply(sender = sender, accountId = account.id, replyToPostId = replyToPostId) ?: return PostNoteResult.ReplyTargetNotFound
            }
        val mention = reply?.mention
        val contentHtml =
            when (val composed = NoteComposer.compose(body = body, mentions = listOfNotNull(mention?.link))) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (imageIds.isEmpty()) return PostNoteResult.EmptyBody else ""
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }

        val prepared =
            publisher.prepare(
                sender = sender,
                contentHtml = contentHtml,
                attachments =
                images.map { image ->
                    NoteAttachment(url = imageUrls.url(image.publicId), mediaType = image.contentType, description = image.description)
                },
                inReplyTo = reply?.inReplyTo,
                mentions = listOfNotNull(mention).map { KotpubNoteMention(actorId = it.record.actorUri, acct = it.record.acct) },
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
                    inboxes = followerRepository.findDeliveryInboxes(sender.username) + listOfNotNull(mention?.record?.inbox),
                    inReplyTo = reply?.inReplyTo,
                    mentions = listOfNotNull(mention?.record),
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

    /**
     * 投稿を消して、フォロワーへの Delete を積む。
     *
     * 付けた画像は記録より先に置き場から消す。記録を先に消すと、画像を消せなかったときに
     * どの投稿にも付いていない画像が置き場に残り、もう辿れない。
     * 画像を消してから記録を消せずに終わっても、投稿は残るので消し直せる
     */
    fun delete(
        username: String,
        publicId: PublicNoteId,
    ): DeleteNoteResult {
        val sender = directory.resolve(username) ?: return DeleteNoteResult.AccountNotFound
        val account = accountService.account(sender.username) ?: return DeleteNoteResult.AccountNotFound
        val prepared =
            publisher.prepareDelete(sender = sender, publicId = KotpubPublicNoteId(publicId.value))
                ?: return DeleteNoteResult.NotFound
        val note = noteRepository.find(publicId) ?: return DeleteNoteResult.NotFound

        if (note.images.isNotEmpty()) {
            val storage = imageStorage ?: return DeleteNoteResult.ImageStorageDisabled
            note.images.forEach { image ->
                val result = storage.delete(ImageStorage.DeleteRequest(ownerAccountId = account.id, path = ImagePath(image.path)))
                if (result is ImageStorage.DeleteResult.Failure) {
                    logger.error("投稿の画像を置き場から消せなかった: ${sender.acct} ${publicId.value} ${image.path}", result.cause)
                    return DeleteNoteResult.ImageDeleteFailed
                }
            }
        }

        val deleted =
            deliveryQueueRepository.enqueueNoteDeletion(
                NoteDeletion(
                    username = sender.username,
                    publicId = publicId,
                    deletedAt = clock.instant(),
                    body = prepared.activityJson,
                    // 宛てた相手にも届けているので、消したことも伝える
                    inboxes = followerRepository.findDeliveryInboxes(sender.username) + note.mentions.mapNotNull { it.inbox },
                ),
            )
        if (!deleted) return DeleteNoteResult.NotFound
        logger.info("投稿を消して Delete を積んだ: ${sender.acct} ${publicId.value}")
        onEnqueued()
        return DeleteNoteResult.Deleted
    }

    /**
     * @return 返信できない投稿なら null
     */
    private fun resolveReply(
        sender: ActorUrls,
        accountId: AccountId,
        replyToPostId: String,
    ): Reply? =
        when (val target = noteRepository.replyTarget(accountId = accountId, member = replyToPostId)) {
            null -> null

            is ReplyTarget.Remote -> {
                // 名前が無いと宛先の表示も acct も作れない
                val preferredUsername = target.preferredUsername ?: return null
                val host = URI(target.actorUri).host ?: return null
                Reply(
                    inReplyTo = target.objectUri,
                    mention =
                    ReplyMention(
                        record = NoteMention(actorUri = target.actorUri, acct = "$preferredUsername@$host", inbox = target.inbox),
                        link =
                        NoteComposer.MentionLink(
                            text = "@$preferredUsername@$host",
                            href = target.profileUrl ?: target.actorUri,
                            username = preferredUsername,
                        ),
                    ),
                )
            }

            is ReplyTarget.Local -> {
                val author = directory.resolve(target.username) ?: return null
                Reply(
                    inReplyTo = NoteUrls(author.domain, KotpubPublicNoteId(target.publicId.value)).noteUrl,
                    // 自分の投稿への返信は、自分に宛てない
                    mention =
                    if (author.username.equals(sender.username, ignoreCase = true)) {
                        null
                    } else {
                        ReplyMention(
                            // このサーバーのアカウントには配らないので inbox は要らない
                            record = NoteMention(actorUri = author.actorId, acct = "${author.username}@${author.domain}", inbox = null),
                            // 画面ではこのサーバーのアカウントをドメイン無しで出すので、その形で書かれる
                            link = NoteComposer.MentionLink(text = "@${author.username}", href = author.actorId, username = author.username),
                        )
                    },
                )
            }
        }

    private fun noteUrl(
        domain: String,
        publicId: PublicNoteId,
    ): String = NoteUrls(domain, KotpubPublicNoteId(publicId.value)).noteUrl

    /**
     * @param mention 宛てるアカウント。宛てなければ null
     */
    private class Reply(
        val inReplyTo: String,
        val mention: ReplyMention?,
    )

    /**
     * @param link 本文に書かれていたらリンクにする
     */
    private class ReplyMention(
        val record: NoteMention,
        val link: NoteComposer.MentionLink,
    )

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
     * 置き場の権限をアカウント単位に絞れるよう、`{yyyy}/{MM}/users/{アカウントの id}/` の下に置く
     */
    fun create(accountId: AccountId, uploadedAt: Instant): ImagePath =
        ImagePath("${ImageUploadMonth.prefix(uploadedAt)}/users/${accountId.value}/note-images/${UUID.randomUUID()}")
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
     * 返信先の投稿が無いか、読めないか、消された
     */
    data object ReplyTargetNotFound : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}

sealed interface DeleteNoteResult {
    data object Deleted : DeleteNoteResult

    /**
     * 無いか、別のアカウントの投稿
     */
    data object NotFound : DeleteNoteResult

    /**
     * 画像の置き場が無く、付けた画像を消せない
     */
    data object ImageStorageDisabled : DeleteNoteResult

    /**
     * 付けた画像を置き場から消せなかった。投稿は残っている
     */
    data object ImageDeleteFailed : DeleteNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : DeleteNoteResult
}
