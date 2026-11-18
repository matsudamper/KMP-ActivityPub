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
import net.matsudamper.kmp.activitypub.repository.NoteUpdate
import net.matsudamper.kmp.activitypub.repository.NotificationActorRef
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.repository.NotificationPostRef
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
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
 * 投稿を記録して、フォロワーへの配信を積む。編集するときは Update を、消すときは Delete を同じく積む。送るのは配信キューのワーカー。
 * 画像は先に [uploadImage] で置き、[post] には置いた画像の id を渡す
 *
 * @param imageStorage null なら画像を置けないので、画像付きの投稿はできない
 * @param remoteUsers 保存していない他のサーバーのアカウントに宛てるときに、相手のサーバーから引いて [remoteActors] に保存する
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 * @param onEdited 編集した後に呼ぶ。投稿を載せている画面に新しい本文を流す
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val accountService: AccountService,
    private val noteRepository: NoteRepository,
    private val imageStorage: ImageStorage?,
    private val imageUrls: NoteImageUrls,
    private val emojiObjects: NoteEmojiObjects,
    private val followerRepository: FollowerRepository,
    private val remoteActors: RemoteActorRepository,
    private val remoteUsers: RemoteUserLookup,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val notifications: NotificationRecorder,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
    private val onPosted: (PublicNoteId) -> Unit,
    private val onEdited: (PublicNoteId) -> Unit,
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
     * 画像があるか引用なら本文は空でもよい
     *
     * @param requestId 画面が送信ごとに作る ID。同じ ID で送り直されたら、新しく作らずに前の投稿を返す
     * @param imageIds [uploadImage] で置いた画像。付けた順に並ぶ
     * @param replyToPostId 返信するタイムラインの投稿の id。投稿者に宛てて、投稿者の inbox にも積む。返信でなければ null
     * @param quotePostId 引用するタイムラインの投稿の id。投稿者の inbox にも積む。引用でなければ null
     * @param mentionAcct 宛てるアカウントの acct。返信と同じく宛てて、他のサーバーのアカウントなら inbox にも積む。宛てなければ null
     */
    suspend fun post(
        username: String,
        body: String,
        requestId: String,
        imageIds: List<NoteImagePublicId>,
        replyToPostId: String?,
        quotePostId: String?,
        mentionAcct: String?,
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
        val quote =
            if (quotePostId == null) {
                null
            } else {
                resolveQuote(accountId = account.id, quotePostId = quotePostId) ?: return PostNoteResult.QuoteTargetNotFound
            }
        val addressed =
            if (mentionAcct == null) {
                null
            } else {
                resolveAddressed(sender = sender, mentionAcct = mentionAcct) ?: return PostNoteResult.MentionTargetNotFound
            }
        val mentions = listOfNotNull(reply?.mention, addressed?.mention).distinctBy { it.record.actorUri }
        val contentHtml =
            when (val composed = NoteComposer.compose(body = body, mentions = mentions.map { it.link })) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (imageIds.isEmpty() && quote == null) return PostNoteResult.EmptyBody else ""
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
                quote = quote?.objectUri,
                mentions = mentions.map { KotpubNoteMention(actorId = it.record.actorUri, acct = it.record.acct) },
                emojis = emojiObjects.find(contentHtml),
            )
        val enqueued =
            deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = sender.username,
                    requestId = requestId,
                    publicId = PublicNoteId(prepared.publicId.value),
                    contentHtml = prepared.contentHtml,
                    sourceText = body.trim(),
                    publishedAt = prepared.publishedAt,
                    images = images,
                    body = prepared.activityJson,
                    inboxes = followerRepository.findDeliveryInboxes(sender.username) + mentions.mapNotNull { it.record.inbox } + listOfNotNull(quote?.authorInbox),
                    inReplyTo = reply?.inReplyTo,
                    quoteUri = quote?.objectUri,
                    quoteInbox = quote?.authorInbox,
                    mentions = mentions.map { it.record },
                ),
            )
        return when (enqueued) {
            is EnqueueNoteResult.Enqueued -> {
                logger.info("投稿を積んだ: ${sender.acct} ${prepared.publicId.value} 宛先=${enqueued.deliveries}")
                onEnqueued()
                onPosted(PublicNoteId(prepared.publicId.value))
                recordLocalMentions(
                    senderAccountId = account.id,
                    publicId = PublicNoteId(prepared.publicId.value),
                    mentions = mentions.map { it.record },
                    postedAt = prepared.publishedAt,
                )
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
     * 編集の下書きに出す本文
     *
     * @return 無いか、別のアカウントの投稿なら null
     */
    fun editableBody(
        username: String,
        publicId: PublicNoteId,
    ): String? {
        val note = noteRepository.find(publicId)?.takeIf { it.username.equals(username, ignoreCase = true) } ?: return null
        return note.sourceText ?: NoteComposer.toPlainText(note.contentHtml)
    }

    /**
     * 本文を書き換えて、投稿を届けた相手へ Update を積む。画像・返信先・引用・宛先は変えない。
     * 画像があるか引用なら本文は空でもよい
     */
    fun edit(
        username: String,
        publicId: PublicNoteId,
        body: String,
    ): EditNoteResult {
        val sender = directory.resolve(username) ?: return EditNoteResult.AccountNotFound
        val note = noteRepository.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) } ?: return EditNoteResult.NotFound
        val contentHtml =
            when (val composed = NoteComposer.compose(body = body, mentions = note.mentions.flatMap { it.toMentionLinks() })) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (note.images.isEmpty() && note.quoteUri == null) return EditNoteResult.EmptyBody else ""
                NoteComposer.ComposeResult.TooLong -> return EditNoteResult.TooLong
            }
        val prepared =
            publisher.prepareUpdate(
                sender = sender,
                publicId = KotpubPublicNoteId(publicId.value),
                contentHtml = contentHtml,
                emojis = emojiObjects.find(contentHtml),
            ) ?: return EditNoteResult.NotFound
        val updated =
            deliveryQueueRepository.enqueueNoteUpdate(
                NoteUpdate(
                    username = sender.username,
                    publicId = publicId,
                    contentHtml = prepared.contentHtml,
                    sourceText = body.trim(),
                    updatedAt = prepared.updatedAt,
                    body = prepared.activityJson,
                    // 投稿を届けた相手には、書き換えたことも伝える
                    inboxes = followerRepository.findDeliveryInboxes(sender.username) + note.mentions.mapNotNull { it.inbox } + listOfNotNull(note.quoteInbox),
                ),
            )
        if (!updated) return EditNoteResult.NotFound
        logger.info("投稿を編集して Update を積んだ: ${sender.acct} ${publicId.value}")
        onEnqueued()
        onEdited(publicId)
        return EditNoteResult.Edited
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
                    // 宛てた相手と引用した投稿の投稿者にも届けているので、消したことも伝える
                    inboxes = followerRepository.findDeliveryInboxes(sender.username) + note.mentions.mapNotNull { it.inbox } + listOfNotNull(note.quoteInbox),
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
                Reply(
                    inReplyTo = target.objectUri,
                    mention =
                    remoteMention(
                        actorUri = target.actorUri,
                        preferredUsername = target.preferredUsername,
                        profileUrl = target.profileUrl,
                        inbox = target.inbox,
                    ) ?: return null,
                )
            }

            is ReplyTarget.Local -> {
                val author = directory.resolve(target.username) ?: return null
                Reply(
                    inReplyTo = NoteUrls(author.domain, KotpubPublicNoteId(target.publicId.value)).noteUrl,
                    mention = localMention(sender = sender, target = author),
                )
            }
        }

    /**
     * 他のサーバーのアカウントは保存済みのアクターを使い、保存していなければ相手のサーバーから引いて保存する
     *
     * @return 宛てるアカウントが見つからなければ null
     */
    private suspend fun resolveAddressed(
        sender: ActorUrls,
        mentionAcct: String,
    ): Addressed? {
        val target = ProfileAcct.parse(mentionAcct, sender.domain) ?: return null
        if (target.isLocal) {
            val account = directory.resolve(target.username) ?: return null
            return Addressed(mention = localMention(sender = sender, target = account))
        }
        val actorUri =
            remoteActors.findByAcct(target.username, target.host)?.actorUri
                ?: remoteUsers.findByAcct(username = target.username, host = target.host)?.actorUri
                ?: return null
        val actor = remoteActors.findByUri(actorUri) ?: return null
        val mention =
            remoteMention(
                actorUri = actor.actorUri,
                preferredUsername = actor.profile.preferredUsername,
                profileUrl = actor.profile.profileUrl,
                inbox = actor.inbox,
            ) ?: return null
        return Addressed(mention = mention)
    }

    /**
     * @return 名前かホストが無く、宛先の表示も acct も作れなければ null
     */
    private fun remoteMention(
        actorUri: String,
        preferredUsername: String?,
        profileUrl: String?,
        inbox: String,
    ): ReplyMention? {
        if (preferredUsername == null) return null
        val host = URI(actorUri).host ?: return null
        return ReplyMention(
            record = NoteMention(actorUri = actorUri, acct = "$preferredUsername@$host", inbox = inbox),
            link =
            NoteComposer.MentionLink(
                text = "@$preferredUsername@$host",
                href = profileUrl ?: actorUri,
                username = preferredUsername,
            ),
        )
    }

    /**
     * 他のサーバーのアカウントには投稿が届いた先で通知されるので、このサーバーのアカウントの分だけ記録する
     */
    private fun recordLocalMentions(senderAccountId: AccountId, publicId: PublicNoteId, mentions: List<NoteMention>, postedAt: Instant) {
        mentions.filter { it.inbox == null }.forEach { mention ->
            notifications.record(
                NotificationEvent.Mentioned(
                    mentionedUsername = mention.acct.substringBefore('@'),
                    post = NotificationPostRef.Local(publicId),
                    actor = NotificationActorRef.Local(senderAccountId),
                    occurredAt = postedAt,
                ),
            )
        }
    }

    /**
     * @return 自分自身には宛てないので、[target] が [sender] なら null
     */
    private fun localMention(
        sender: ActorUrls,
        target: ActorUrls,
    ): ReplyMention? {
        if (target.username.equals(sender.username, ignoreCase = true)) return null
        return ReplyMention(
            // このサーバーのアカウントには配らないので inbox は要らない
            record = NoteMention(actorUri = target.actorId, acct = "${target.username}@${target.domain}", inbox = null),
            // 画面ではこのサーバーのアカウントをドメイン無しで出すので、その形で書かれる
            link = NoteComposer.MentionLink(text = "@${target.username}", href = target.actorId, username = target.username),
        )
    }

    /**
     * @return 引用できない投稿なら null
     */
    private fun resolveQuote(
        accountId: AccountId,
        quotePostId: String,
    ): Quote? =
        when (val target = noteRepository.replyTarget(accountId = accountId, member = quotePostId)) {
            null -> null

            is ReplyTarget.Remote -> Quote(objectUri = target.objectUri, authorInbox = target.inbox)

            is ReplyTarget.Local -> {
                val author = directory.resolve(target.username) ?: return null
                // このサーバーのアカウントには配らないので inbox は要らない
                Quote(objectUri = NoteUrls(author.domain, KotpubPublicNoteId(target.publicId.value)).noteUrl, authorInbox = null)
            }
        }

    /**
     * 他のサーバーのアカウントは、書いた `@alice@example.com` と、書いたままの本文を記録する前の投稿から戻した `@alice` の両方をリンクにする
     */
    private fun NoteMention.toMentionLinks(): List<NoteComposer.MentionLink> {
        val mentionUsername = acct.substringBefore('@')
        val shortLink = NoteComposer.MentionLink(text = "@$mentionUsername", href = actorUri, username = mentionUsername)
        // このサーバーのアカウントは画面に合わせてドメイン無しで書かれる
        if (inbox == null) return listOf(shortLink)
        return listOf(NoteComposer.MentionLink(text = "@$acct", href = actorUri, username = mentionUsername), shortLink)
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
     * @param mention 宛てるアカウント。自分自身なら宛てないので null
     */
    private class Addressed(
        val mention: ReplyMention?,
    )

    /**
     * @param authorInbox 引用した投稿の投稿者の inbox。このサーバーの投稿なら null
     */
    private class Quote(
        val objectUri: String,
        val authorInbox: String?,
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
     * 本文も画像も無く、引用でもない
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
     * 引用する投稿が無いか、読めないか、消された
     */
    data object QuoteTargetNotFound : PostNoteResult

    /**
     * 宛てるアカウントが無いか、相手のサーバーから引けない
     */
    data object MentionTargetNotFound : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}

sealed interface EditNoteResult {
    data object Edited : EditNoteResult

    /**
     * 無いか、別のアカウントの投稿
     */
    data object NotFound : EditNoteResult

    /**
     * 本文が空で、画像も引用も無い
     */
    data object EmptyBody : EditNoteResult

    data object TooLong : EditNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : EditNoteResult
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
