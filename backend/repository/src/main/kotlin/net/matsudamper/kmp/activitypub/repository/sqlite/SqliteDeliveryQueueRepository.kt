package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWERS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.DSLContext

internal class SqliteDeliveryQueueRepository(
    private val jooq: SqliteJooq,
) : DeliveryQueueRepository {
    override fun enqueueNote(post: NotePost): EnqueueNoteResult = jooq.transaction { dsl ->
        val accountId =
            dsl.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.USERNAME.eq(post.username.lowercase()))
                .fetchOne(ACCOUNTS.ID)
                ?: return@transaction EnqueueNoteResult.AccountNotFound
        val alreadyPostedPublicId =
            dsl.select(NOTES.PUBLIC_ID)
                .from(NOTES)
                .where(NOTES.ACCOUNT_ID.eq(accountId))
                .and(NOTES.REQUEST_ID.eq(post.requestId))
                .fetchOne(NOTES.PUBLIC_ID)
        if (alreadyPostedPublicId != null) {
            return@transaction EnqueueNoteResult.AlreadyPosted(PublicNoteId(alreadyPostedPublicId))
        }
        val publishedAt = StoredInstant.format(post.publishedAt)

        val noteId =
            checkNotNull(
                dsl.insertInto(NOTES)
                    .set(NOTES.ACCOUNT_ID, accountId)
                    .set(NOTES.PUBLIC_ID, post.publicId.value)
                    .set(NOTES.CONTENT_HTML, post.contentHtml)
                    .set(NOTES.PUBLISHED_AT, publishedAt)
                    .set(NOTES.REQUEST_ID, post.requestId)
                    .returning(NOTES.ID)
                    .fetchOne(NOTES.ID),
            ) { "記録した投稿の id を引けない: ${post.publicId}" }

        val inboxes = post.inboxes.distinct()
        inboxes.forEach { inbox ->
            dsl.insertInto(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_CREATE_NOTE)
                .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId)
                .set(DELIVERY_QUEUE.NOTE_ID, noteId)
                .set(DELIVERY_QUEUE.INBOX, inbox)
                .set(DELIVERY_QUEUE.BODY, post.body)
                .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
                .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
                .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, publishedAt)
                .set(DELIVERY_QUEUE.ENQUEUED_AT, publishedAt)
                .execute()
        }
        EnqueueNoteResult.Enqueued(deliveries = inboxes.size)
    }

    override fun updateProfile(update: ProfileUpdate): Boolean = jooq.transaction { dsl ->
        val accountId =
            dsl.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.USERNAME.eq(update.username.lowercase()))
                .fetchOne(ACCOUNTS.ID)
                ?: return@transaction false
        dsl.update(ACCOUNTS)
            .set(ACCOUNTS.DISPLAY_NAME, update.displayName)
            .where(ACCOUNTS.ID.eq(accountId))
            .execute()

        // 送っている途中の行は消しても送信が止まらないので、待っている行だけ消す
        dsl.deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId))
            .and(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_UPDATE_ACTOR))
            .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
            .execute()

        val updatedAt = StoredInstant.format(update.updatedAt)
        update.inboxes.distinct().forEach { inbox ->
            dsl.insertInto(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_UPDATE_ACTOR)
                .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId)
                .set(DELIVERY_QUEUE.INBOX, inbox)
                .set(DELIVERY_QUEUE.BODY, update.body)
                .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
                .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
                .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, updatedAt)
                .set(DELIVERY_QUEUE.ENQUEUED_AT, updatedAt)
                .execute()
        }
        true
    }

    override fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery> = jooq.transaction { dsl ->
        val due =
            dsl.select(
                DELIVERY_QUEUE.ID,
                ACCOUNTS.USERNAME,
                DELIVERY_QUEUE.INBOX,
                DELIVERY_QUEUE.BODY,
                DELIVERY_QUEUE.ATTEMPTS,
                DELIVERY_QUEUE.ENQUEUED_AT,
            )
                .from(DELIVERY_QUEUE)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(DELIVERY_QUEUE.ACCOUNT_ID))
                .where(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
                .and(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.le(StoredInstant.format(now)))
                .orderBy(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, DELIVERY_QUEUE.ID)
                .limit(limit)
                .fetch { record ->
                    ClaimedDelivery(
                        id = checkNotNull(record.get(DELIVERY_QUEUE.ID)),
                        username = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                        inbox = checkNotNull(record.get(DELIVERY_QUEUE.INBOX)),
                        body = checkNotNull(record.get(DELIVERY_QUEUE.BODY)),
                        attempts = checkNotNull(record.get(DELIVERY_QUEUE.ATTEMPTS)).toInt() + 1,
                        enqueuedAt = StoredInstant.parse(checkNotNull(record.get(DELIVERY_QUEUE.ENQUEUED_AT))),
                    )
                }
        if (due.isEmpty()) return@transaction listOf()

        dsl.update(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_DELIVERING)
            .set(DELIVERY_QUEUE.ATTEMPTS, DELIVERY_QUEUE.ATTEMPTS.plus(1L))
            .where(DELIVERY_QUEUE.ID.`in`(due.map { it.id }))
            .execute()
        due
    }

    override fun markDelivered(
        id: Long,
        deliveredAt: Instant,
    ) {
        jooq.transaction { dsl ->
            val row = dsl.findRow(id) ?: return@transaction
            dsl.deleteFrom(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.ID.eq(id))
                .execute()
            if (row.kind == DeliveryQueueValues.KIND_ACCEPT_FOLLOW) {
                dsl.update(ACCOUNT_FOLLOWERS)
                    .set(ACCOUNT_FOLLOWERS.ACCEPTED_AT, StoredInstant.format(deliveredAt))
                    .where(ACCOUNT_FOLLOWERS.ACCOUNT_ID.eq(row.accountId))
                    .and(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID.eq(row.remoteActorId))
                    .and(ACCOUNT_FOLLOWERS.ACCEPTED_AT.isNull)
                    .execute()
            }
        }
    }

    override fun scheduleRetry(
        id: Long,
        nextAttemptAt: Instant,
        error: String,
    ) {
        jooq.withConnection { dsl ->
            dsl.update(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
                .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, StoredInstant.format(nextAttemptAt))
                .set(DELIVERY_QUEUE.LAST_ERROR, error)
                .where(DELIVERY_QUEUE.ID.eq(id))
                .execute()
        }
    }

    override fun giveUp(
        id: Long,
        error: String,
    ) {
        jooq.transaction { dsl ->
            val row = dsl.findRow(id) ?: return@transaction
            dsl.update(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_FAILED)
                .setNull(DELIVERY_QUEUE.NEXT_ATTEMPT_AT)
                .setNull(DELIVERY_QUEUE.BODY)
                .set(DELIVERY_QUEUE.LAST_ERROR, error)
                .where(DELIVERY_QUEUE.ID.eq(id))
                .execute()
            if (row.kind == DeliveryQueueValues.KIND_ACCEPT_FOLLOW) {
                // 参照したままフォローを消すと、外部キーで failed の行まで消える
                dsl.update(DELIVERY_QUEUE)
                    .setNull(DELIVERY_QUEUE.REMOTE_ACTOR_ID)
                    .where(DELIVERY_QUEUE.ID.eq(id))
                    .execute()
                dsl.deleteFrom(ACCOUNT_FOLLOWERS)
                    .where(ACCOUNT_FOLLOWERS.ACCOUNT_ID.eq(row.accountId))
                    .and(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID.eq(row.remoteActorId))
                    .and(ACCOUNT_FOLLOWERS.ACCEPTED_AT.isNull)
                    .execute()
            }
        }
    }

    override fun recoverDelivering(now: Instant): Int = jooq.withConnection { dsl ->
        dsl.update(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
            .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, StoredInstant.format(now))
            .where(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_DELIVERING))
            .execute()
    }

    private fun DSLContext.findRow(id: Long): QueueRow? =
        select(DELIVERY_QUEUE.KIND, DELIVERY_QUEUE.ACCOUNT_ID, DELIVERY_QUEUE.REMOTE_ACTOR_ID)
            .from(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.ID.eq(id))
            .and(DELIVERY_QUEUE.STATE.ne(DeliveryQueueValues.STATE_FAILED))
            .fetchOne { record ->
                QueueRow(
                    kind = checkNotNull(record.get(DELIVERY_QUEUE.KIND)),
                    accountId = checkNotNull(record.get(DELIVERY_QUEUE.ACCOUNT_ID)),
                    remoteActorId = record.get(DELIVERY_QUEUE.REMOTE_ACTOR_ID),
                )
            }

    private data class QueueRow(
        val kind: String,
        val accountId: Long,
        val remoteActorId: Long?,
    )
}
