package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingBoost
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_REMOTE_NOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_REMOTE_NOTE_BOOST_INBOXES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.DSLContext
import org.jooq.Record3
import org.jooq.impl.DSL

internal class SqliteAccountBoostRepository(
    private val jooq: SqliteJooq,
) : AccountBoostRepository {
    override fun target(accountId: AccountId, member: String): BoostTarget? {
        val boostId = TimelineMembers.boostIdOf(member)
        if (boostId != null) {
            return jooq.withConnection { dsl ->
                dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.INBOX)
                    .from(REMOTE_BOOSTS)
                    .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_BOOSTS.REMOTE_NOTE_ID))
                    .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                    .where(REMOTE_BOOSTS.ID.eq(boostId))
                    .and(dsl.remoteBoostReadableBy(accountId))
                    .fetchOne { it.toBoostTarget() }
            }
        }
        val remoteNoteId = TimelineMembers.remoteNoteIdOf(member) ?: return null
        return jooq.withConnection { dsl ->
            dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.INBOX)
                .from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.ID.eq(remoteNoteId))
                .and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))
                .and(dsl.remoteNoteReadableBy(accountId))
                .fetchOne { it.toBoostTarget() }
        }
    }

    override fun boostAndEnqueue(boost: OutgoingBoost): Boolean {
        return jooq.transaction { dsl ->
            val remoteNoteId = dsl.remoteNoteIdOfMember(boost.member) ?: return@transaction false
            val now = StoredInstant.format(boost.boostedAt)
            val inserted = dsl.insertInto(ACCOUNT_REMOTE_NOTE_BOOSTS)
                .set(ACCOUNT_REMOTE_NOTE_BOOSTS.ACCOUNT_ID, boost.accountId.value)
                .set(ACCOUNT_REMOTE_NOTE_BOOSTS.REMOTE_NOTE_ID, remoteNoteId)
                .set(ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_ACTIVITY_URI, boost.announceId)
                .set(ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_BODY, boost.body)
                .set(ACCOUNT_REMOTE_NOTE_BOOSTS.CREATED_AT, now)
                .onConflictDoNothing()
                .execute() > 0
            if (inserted) {
                boost.inboxes.distinct().forEach { inbox ->
                    dsl.insertInto(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES)
                        .set(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.ACCOUNT_ID, boost.accountId.value)
                        .set(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.REMOTE_NOTE_ID, remoteNoteId)
                        .set(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.INBOX, inbox)
                        .execute()
                    dsl.enqueue(boost.accountId, inbox, boost.body, now, DeliveryQueueValues.KIND_ANNOUNCE)
                }
            }
            inserted
        }
    }

    override fun unboostAndEnqueueUndo(
        accountId: AccountId,
        member: String,
        now: Instant,
        undoBody: (announceBody: String) -> String,
    ): Boolean {
        return jooq.transaction { dsl ->
            val remoteNoteId = dsl.remoteNoteIdOfMember(member) ?: return@transaction false
            val announcedInboxes = dsl.select(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.INBOX).from(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES)
                .where(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.ACCOUNT_ID.eq(accountId.value))
                .and(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.REMOTE_NOTE_ID.eq(remoteNoteId))
                .fetch(ACCOUNT_REMOTE_NOTE_BOOST_INBOXES.INBOX)
                .filterNotNull()
            val removed = dsl.deleteFrom(ACCOUNT_REMOTE_NOTE_BOOSTS)
                .where(ACCOUNT_REMOTE_NOTE_BOOSTS.ACCOUNT_ID.eq(accountId.value))
                .and(ACCOUNT_REMOTE_NOTE_BOOSTS.REMOTE_NOTE_ID.eq(remoteNoteId))
                .returning(ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_ACTIVITY_URI, ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_BODY)
                .fetchOne()
                ?: return@transaction false
            val announceId = checkNotNull(removed.get(ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_ACTIVITY_URI))
            val announceBody = checkNotNull(removed.get(ACCOUNT_REMOTE_NOTE_BOOSTS.ANNOUNCE_BODY))
            val matchingAnnounce = DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId.value)
                .and(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_ANNOUNCE))
                .and(DSL.field("json_extract({0}, '$.id')", String::class.java, DELIVERY_QUEUE.BODY).eq(announceId))
            val unsentInboxes = dsl.deleteFrom(DELIVERY_QUEUE).where(matchingAnnounce)
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
                .and(DELIVERY_QUEUE.ATTEMPTS.eq(0L))
                .returning(DELIVERY_QUEUE.INBOX)
                .fetch(DELIVERY_QUEUE.INBOX)
                .filterNotNull()
                .toSet()
            // 送り直し待ちの Announce は相手に届いているかもしれないので、取り消した上で Undo も送る
            dsl.deleteFrom(DELIVERY_QUEUE).where(matchingAnnounce)
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
                .execute()
            // 送っている途中の行は消しても送信が止まらない。失敗したときに送り直して Undo の後に届かないよう、印を付ける
            dsl.update(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_CANCELLED_ANNOUNCE)
                .where(matchingAnnounce)
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_DELIVERING))
                .execute()
            val undoInboxes = announcedInboxes.filterNot { it in unsentInboxes }
            if (undoInboxes.isNotEmpty()) {
                val body = undoBody(announceBody)
                val enqueuedAt = StoredInstant.format(now)
                undoInboxes.forEach { inbox ->
                    dsl.enqueue(accountId, inbox, body, enqueuedAt, DeliveryQueueValues.KIND_UNDO_ANNOUNCE)
                }
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
     * ブーストで載った投稿は、ブーストされた元の投稿を指す
     */
    private fun DSLContext.remoteNoteIdOfMember(member: String): Long? {
        val boostId = TimelineMembers.boostIdOf(member) ?: return TimelineMembers.remoteNoteIdOf(member)
        return select(REMOTE_BOOSTS.REMOTE_NOTE_ID).from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.ID.eq(boostId)).fetchOne(REMOTE_BOOSTS.REMOTE_NOTE_ID)
    }

    private fun Record3<String?, String?, String?>.toBoostTarget(): BoostTarget =
        BoostTarget(objectUri = checkNotNull(value1()), authorActorUri = checkNotNull(value2()), authorInbox = checkNotNull(value3()))
}
