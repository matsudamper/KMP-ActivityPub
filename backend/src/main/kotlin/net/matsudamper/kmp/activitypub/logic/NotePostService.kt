package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.note.NotePublisher
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積む。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val onEnqueued: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(NotePostService::class.java)

    fun post(
        username: String,
        body: String,
    ): PostNoteResult {
        val contentHtml =
            when (val composed = NoteComposer.compose(body)) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> return PostNoteResult.EmptyBody
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }
        val sender = directory.resolve(username) ?: return PostNoteResult.AccountNotFound

        val prepared = publisher.prepare(sender = sender, contentHtml = contentHtml)
        val deliveries =
            deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = sender.username,
                    publicId = PublicNoteId(prepared.publicId.value),
                    contentHtml = prepared.contentHtml,
                    publishedAt = prepared.publishedAt,
                    body = prepared.activityJson,
                    inboxes = followerRepository.findDeliveryInboxes(sender.username),
                ),
            ) ?: return PostNoteResult.AccountNotFound
        logger.info("投稿を積んだ: ${sender.acct} ${prepared.publicId.value} 宛先=$deliveries")
        onEnqueued()

        return PostNoteResult.Posted(publicId = PublicNoteId(prepared.publicId.value), url = prepared.url)
    }
}

sealed interface PostNoteResult {
    data class Posted(
        val publicId: PublicNoteId,
        val url: String,
    ) : PostNoteResult

    data object EmptyBody : PostNoteResult

    data object TooLong : PostNoteResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : PostNoteResult
}
