package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NewNote
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.PreparedNote
import org.slf4j.LoggerFactory

/**
 * kotpub の `core` の [NotePublisher] が `Create{Note}` を組み立て、
 * `:backend:repository` の投函の口が記録と投函を 1 トランザクションで書く。
 * 両方を知っているのは `:backend` だけなので、繋ぐのはここになる。
 *
 * 配信はここでは行わない。投函した行は配信ワーカーが拾って送る。
 */
class NoteEnqueuer(
    private val publisher: NotePublisher,
    private val followers: FollowerRepository,
    private val deliveryQueue: DeliveryQueueRepository,
    private val domainBlocks: DomainBlockService,
) {
    private val logger = LoggerFactory.getLogger(NoteEnqueuer::class.java)

    /**
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと
     */
    fun enqueue(
        sender: ActorUrls,
        contentHtml: String,
    ): QueuedNote {
        val prepared = publisher.prepare(sender = sender, contentHtml = contentHtml)
        // 止めているドメインの行は配信ワーカーが送らずに failed にするだけで、消えずに溜まり続ける
        val inboxes = followers.deliveryTargets(sender.username)
            .filterNot { domainBlocks.blocksDeliveryTo(it) }

        val result = deliveryQueue.enqueueNote(
            NotePost(
                note = prepared.toNewNote(sender),
                body = prepared.activityJson,
                inboxes = inboxes,
                enqueuedAt = prepared.publishedAt,
            ),
        )

        val deliveries = when (result) {
            is EnqueueNoteResult.Queued -> result.deliveries
        }
        logger.info("投稿を投函した: ${sender.acct} ${prepared.publicId} 宛先=$deliveries")
        return prepared.toQueuedNote()
    }

    private fun PreparedNote.toNewNote(sender: ActorUrls): NewNote = NewNote(
        username = sender.username,
        publicId = PublicNoteId(publicId.value),
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )

    private fun PreparedNote.toQueuedNote(): QueuedNote = QueuedNote(
        publicId = publicId,
        url = url,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}

data class QueuedNote(
    val publicId: MastodonPublicNoteId,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
)
