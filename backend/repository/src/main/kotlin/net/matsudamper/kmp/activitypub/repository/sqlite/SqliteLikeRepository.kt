package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikeTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_LIKES
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqliteLikeRepository(
    private val jooq: SqliteJooq,
) : LikeRepository {
    override fun target(accountId: AccountId, member: String): LikeTarget? {
        val localNoteId = TimelineMembers.localNoteIdOf(member)
        if (localNoteId != null) {
            // このサーバーの投稿は outbox で公開しているので、誰でも読める
            val exists = jooq.withConnection { dsl -> dsl.fetchExists(NOTES, NOTES.ID.eq(localNoteId)) }
            return if (exists) LikeTarget.Local else null
        }
        val boostId = TimelineMembers.boostIdOf(member)
        if (boostId != null) {
            return jooq.withConnection { dsl ->
                dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.INBOX)
                    .from(REMOTE_BOOSTS)
                    .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_BOOSTS.REMOTE_NOTE_ID))
                    .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                    .where(REMOTE_BOOSTS.ID.eq(boostId))
                    .and(dsl.remoteBoostReadableBy(accountId))
                    .fetchOne { LikeTarget.Remote(objectUri = checkNotNull(it.value1()), inbox = checkNotNull(it.value2())) }
            }
        }
        val remoteNoteId = TimelineMembers.remoteNoteIdOf(member) ?: return null
        return jooq.withConnection { dsl ->
            dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.INBOX)
                .from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.ID.eq(remoteNoteId))
                // 公開している投稿は誰でも読めるので、保存していればフォローしていなくてもいいねできる
                .and(dsl.remoteNoteReadableOrBoostedTo(accountId).or(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES)))
                .fetchOne { LikeTarget.Remote(objectUri = checkNotNull(it.value1()), inbox = checkNotNull(it.value2())) }
        }
    }

    override fun likeLocal(accountId: AccountId, member: String, likedAt: Instant): Boolean {
        val localNoteId = TimelineMembers.localNoteIdOf(member) ?: return false
        return jooq.withConnection { dsl ->
            dsl.insertInto(NOTE_LIKES)
                .set(NOTE_LIKES.ACCOUNT_ID, accountId.value)
                .set(NOTE_LIKES.NOTE_ID, localNoteId)
                .set(NOTE_LIKES.CREATED_AT, StoredInstant.format(likedAt))
                .onConflictDoNothing()
                .execute() > 0
        }
    }

    override fun unlikeLocal(accountId: AccountId, member: String): Boolean {
        val localNoteId = TimelineMembers.localNoteIdOf(member) ?: return false
        return jooq.withConnection { dsl ->
            dsl.deleteFrom(NOTE_LIKES)
                .where(NOTE_LIKES.ACCOUNT_ID.eq(accountId.value))
                .and(NOTE_LIKES.NOTE_ID.eq(localNoteId))
                .execute() > 0
        }
    }

    override fun likedLocalNotes(accountId: AccountId, notePublicIds: Set<PublicNoteId>): Set<PublicNoteId> {
        if (notePublicIds.isEmpty()) return setOf()
        return jooq.withConnection { dsl ->
            dsl.select(NOTES.PUBLIC_ID)
                .from(NOTE_LIKES)
                .join(NOTES).on(NOTES.ID.eq(NOTE_LIKES.NOTE_ID))
                .where(NOTE_LIKES.ACCOUNT_ID.eq(accountId.value))
                .and(NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .fetchSet { PublicNoteId(checkNotNull(it.value1())) }
        }
    }

    override fun likeAndEnqueue(like: OutgoingLike): Boolean {
        return jooq.transaction { dsl ->
            val remoteNoteId = dsl.likedRemoteNoteIdOf(like.member) ?: return@transaction false
            val now = StoredInstant.format(like.likedAt)
            val inserted = dsl.insertInto(REMOTE_NOTE_LIKES)
                .set(REMOTE_NOTE_LIKES.ACCOUNT_ID, like.accountId.value)
                .set(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID, remoteNoteId)
                .set(REMOTE_NOTE_LIKES.LIKE_ACTIVITY_URI, like.likeId)
                .set(REMOTE_NOTE_LIKES.CREATED_AT, now)
                .onConflictDoNothing()
                .execute() > 0
            if (inserted) {
                dsl.enqueue(like.accountId, like.inbox, like.body, now, DeliveryQueueValues.KIND_LIKE)
            }
            inserted
        }
    }

    override fun unlikeAndEnqueueUndo(
        accountId: AccountId,
        member: String,
        now: Instant,
        undoBody: (likeId: String, objectUri: String) -> String,
    ): Boolean {
        return jooq.transaction { dsl ->
            val remoteNoteId = dsl.likedRemoteNoteIdOf(member) ?: return@transaction false
            val note = dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.INBOX)
                .from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.ID.eq(remoteNoteId))
                .fetchOne()
                ?: return@transaction false
            val objectUri = checkNotNull(note.value1())
            val inbox = checkNotNull(note.value2())
            val likeId = dsl.deleteFrom(REMOTE_NOTE_LIKES)
                .where(REMOTE_NOTE_LIKES.ACCOUNT_ID.eq(accountId.value))
                .and(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID.eq(remoteNoteId))
                .returning(REMOTE_NOTE_LIKES.LIKE_ACTIVITY_URI)
                .fetchOne(REMOTE_NOTE_LIKES.LIKE_ACTIVITY_URI)
                ?: return@transaction false
            val matchingLike = DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId.value)
                .and(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_LIKE))
                .and(DSL.field("json_extract({0}, '$.id')", String::class.java, DELIVERY_QUEUE.BODY).eq(likeId))
            val cancelledUnsent = dsl.deleteFrom(DELIVERY_QUEUE).where(matchingLike)
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
                .and(DELIVERY_QUEUE.ATTEMPTS.eq(0L))
                .execute() > 0
            if (!cancelledUnsent) {
                // 送り直し待ちの Like は相手に届いているかもしれないので、取り消した上で Undo も送る
                dsl.deleteFrom(DELIVERY_QUEUE).where(matchingLike)
                    .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
                    .execute()
                // 送っている途中の行は消しても送信が止まらない。失敗したときに送り直して Undo の後に届かないよう、印を付ける
                dsl.update(DELIVERY_QUEUE)
                    .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_CANCELLED_LIKE)
                    .where(matchingLike)
                    .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_DELIVERING))
                    .execute()
                dsl.enqueue(accountId, inbox, undoBody(likeId, objectUri), StoredInstant.format(now), DeliveryQueueValues.KIND_UNDO_LIKE)
            }
            true
        }
    }

    private fun DSLContext.enqueue(accountId: AccountId, inbox: String, body: String, now: String, kind: String) {
        insertInto(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.KIND, kind)
            .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId.value)
            .set(DELIVERY_QUEUE.INBOX, inbox)
            .set(DELIVERY_QUEUE.BODY, body)
            .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
            .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
            .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, now)
            .set(DELIVERY_QUEUE.ENQUEUED_AT, now)
            .execute()
    }

    /**
     * ブーストで載った投稿へのいいねは、ブーストされた元の投稿に付ける
     */
    private fun DSLContext.likedRemoteNoteIdOf(member: String): Long? {
        val boostId = TimelineMembers.boostIdOf(member) ?: return TimelineMembers.remoteNoteIdOf(member)
        return select(REMOTE_BOOSTS.REMOTE_NOTE_ID).from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.ID.eq(boostId)).fetchOne(REMOTE_BOOSTS.REMOTE_NOTE_ID)
    }
}
