package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * 投稿を記録して、フォロワーへの配信を積み、ホームタイムラインに並べる。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class NotePostService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val homeTimelineService: HomeTimelineService,
    private val onEnqueued: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(NotePostService::class.java)

    /**
     * @param requestId 画面が送信ごとに作る ID。同じ ID で送り直されたら、新しく作らずに前の投稿を返す
     */
    fun post(
        account: Account,
        body: String,
        requestId: String,
    ): PostNoteResult {
        val contentHtml =
            when (val composed = NoteComposer.compose(body)) {
                is NoteComposer.ComposeResult.Composed -> composed.contentHtml
                NoteComposer.ComposeResult.Empty -> return PostNoteResult.EmptyBody
                NoteComposer.ComposeResult.TooLong -> return PostNoteResult.TooLong
            }
        val sender = directory.resolve(account.username) ?: return PostNoteResult.AccountNotFound

        val prepared = publisher.prepare(sender = sender, contentHtml = contentHtml)
        val enqueued =
            deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = sender.username,
                    requestId = requestId,
                    publicId = PublicNoteId(prepared.publicId.value),
                    contentHtml = prepared.contentHtml,
                    publishedAt = prepared.publishedAt,
                    body = prepared.activityJson,
                    inboxes = followerRepository.findDeliveryInboxes(sender.username),
                ),
            )
        return when (enqueued) {
            is EnqueueNoteResult.Enqueued -> {
                logger.info("投稿を積んだ: ${sender.acct} ${prepared.publicId.value} 宛先=${enqueued.deliveries}")
                homeTimelineService.onNotePosted(
                    authorId = account.id,
                    position = NotePosition(publishedAt = prepared.publishedAt, publicId = PublicNoteId(prepared.publicId.value)),
                )
                onEnqueued()
                PostNoteResult.Posted(publicId = PublicNoteId(prepared.publicId.value), url = prepared.url)
            }

            is EnqueueNoteResult.AlreadyPosted -> {
                logger.info("送り直された投稿なので新しく作らない: ${sender.acct} ${enqueued.publicId.value}")
                PostNoteResult.Posted(
                    publicId = enqueued.publicId,
                    url = NoteUrls(sender.domain, KotpubPublicNoteId(enqueued.publicId.value)).noteUrl,
                )
            }

            EnqueueNoteResult.AccountNotFound -> PostNoteResult.AccountNotFound
        }
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
