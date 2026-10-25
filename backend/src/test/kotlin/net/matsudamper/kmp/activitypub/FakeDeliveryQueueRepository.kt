package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate

/**
 * 積んだ投稿を覚えるだけで、取り出しには何も返さない
 */
internal class FakeDeliveryQueueRepository(
    private val notes: FakeNoteRepository,
) : DeliveryQueueRepository {
    val posts = mutableListOf<NotePost>()
    val profileUpdates = mutableListOf<ProfileUpdate>()

    override fun enqueueNote(post: NotePost): EnqueueNoteResult {
        val alreadyPosted = posts.firstOrNull { it.username.equals(post.username, ignoreCase = true) && it.requestId == post.requestId }
        if (alreadyPosted != null) return EnqueueNoteResult.AlreadyPosted(alreadyPosted.publicId)
        posts += post
        notes.notes += Note(publicId = post.publicId, username = post.username, contentHtml = post.contentHtml, publishedAt = post.publishedAt)
        return EnqueueNoteResult.Enqueued(deliveries = post.inboxes.distinct().size)
    }

    override fun updateProfile(update: ProfileUpdate): Boolean {
        profileUpdates += update
        return true
    }

    override fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery> = listOf()

    override fun markDelivered(
        id: Long,
        deliveredAt: Instant,
    ) = Unit

    override fun scheduleRetry(
        id: Long,
        nextAttemptAt: Instant,
        error: String,
    ) = Unit

    override fun giveUp(
        id: Long,
        error: String,
    ) = Unit

    override fun recoverDelivering(now: Instant): Int = 0
}
