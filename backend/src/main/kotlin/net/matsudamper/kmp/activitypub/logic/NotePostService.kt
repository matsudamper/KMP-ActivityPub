package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImageId
import net.matsudamper.kmp.activitypub.repository.NoteImageRepository
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteUrls
import net.matsudamper.kotpub.note.StoredNoteAttachment
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積む。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val noteRepository: NoteRepository,
    private val noteImageRepository: NoteImageRepository,
    private val noteImageUrls: NoteImageUrls,
    private val publisher: NotePublisher,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val onEnqueued: () -> Unit,
    private val onPosted: (PublicNoteId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(NotePostService::class.java)

    /**
     * @param requestId 画面が送信ごとに作る ID。同じ ID で送り直されたら、新しく作らずに前の投稿を返す
     * @param imageIds [NoteImageService] で置いた画像。この順に並べる
     */
    fun post(
        accountId: AccountId,
        username: String,
        body: String,
        requestId: String,
        imageIds: List<NoteImageId>,
    ): PostNoteResult {
        if (imageIds.size > MAX_IMAGES) return PostNoteResult.TooManyImages
        if (imageIds.distinct().size != imageIds.size) return PostNoteResult.ImageNotFound
        val contentHtml =
            when (val composed = NoteComposer.compose(body)) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> if (imageIds.isEmpty()) return PostNoteResult.EmptyBody else ""
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }
        val sender = directory.resolve(username) ?: return PostNoteResult.AccountNotFound
        // 送り直しなら画像はもう前の投稿に添えてあるので、探す前に前の投稿を返す
        val alreadyPosted = noteRepository.findPublicIdByRequestId(username = sender.username, requestId = requestId)
        if (alreadyPosted != null) return alreadyPosted(sender = sender, publicId = alreadyPosted)

        val images = noteImageRepository.findUnattached(accountId = accountId, ids = imageIds)
        if (images.size != imageIds.size) return PostNoteResult.ImageNotFound

        val prepared =
            publisher.prepare(
                sender = sender,
                contentHtml = contentHtml,
                attachments = images.map { it.toStoredNoteAttachment(noteImageUrls) },
            )
        val enqueued =
            deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = sender.username,
                    requestId = requestId,
                    publicId = PublicNoteId(prepared.publicId.value),
                    contentHtml = prepared.contentHtml,
                    publishedAt = prepared.publishedAt,
                    imageIds = imageIds,
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

            is EnqueueNoteResult.AlreadyPosted -> alreadyPosted(sender = sender, publicId = enqueued.publicId)

            EnqueueNoteResult.AccountNotFound -> PostNoteResult.AccountNotFound

            // 探してから記録するまでの間に、同じ画像を別の投稿に添えられた
            EnqueueNoteResult.ImageNotAvailable -> PostNoteResult.ImageNotFound
        }
    }

    private fun alreadyPosted(
        sender: ActorUrls,
        publicId: PublicNoteId,
    ): PostNoteResult.Posted {
        logger.info("送り直された投稿なので新しく作らない: ${sender.acct} ${publicId.value}")
        return PostNoteResult.Posted(
            publicId = publicId,
            url = NoteUrls(sender.domain, KotpubPublicNoteId(publicId.value)).noteUrl,
        )
    }

    companion object {
        /**
         * Mastodon の 1 投稿あたりの画像の上限
         */
        const val MAX_IMAGES: Int = 4
    }
}

internal fun NoteImage.toStoredNoteAttachment(urls: NoteImageUrls): StoredNoteAttachment =
    StoredNoteAttachment(
        url = urls.url(path),
        mediaType = mediaType,
        description = description,
        width = width,
        height = height,
    )

sealed interface PostNoteResult {
    data class Posted(
        val publicId: PublicNoteId,
        val url: String,
    ) : PostNoteResult

    data object EmptyBody : PostNoteResult

    data object TooLong : PostNoteResult

    data object TooManyImages : PostNoteResult

    /**
     * 置いていないか、別の投稿に添えたか、別のアカウントの画像
     */
    data object ImageNotFound : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}
