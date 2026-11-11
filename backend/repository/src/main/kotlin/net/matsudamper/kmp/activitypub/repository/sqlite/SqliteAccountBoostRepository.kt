package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.AccountBoostPosition
import net.matsudamper.kmp.activitypub.repository.AccountBoostRecord
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingBoost
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOST_INBOXES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record4
import org.jooq.impl.DSL

internal class SqliteAccountBoostRepository(
    private val jooq: SqliteJooq,
) : AccountBoostRepository {
    override fun target(accountId: AccountId, member: String): BoostTarget? {
        val localNoteId = TimelineMembers.localNoteIdOf(member)
        if (localNoteId != null) {
            // このサーバーの投稿は outbox で公開しているので、誰でも読める
            return jooq.withConnection { dsl ->
                dsl.select(NOTES.PUBLIC_ID, ACCOUNTS.USERNAME).from(NOTES).join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
                    .where(NOTES.ID.eq(localNoteId))
                    .fetchOne { BoostTarget.Local(publicId = PublicNoteId(checkNotNull(it.value1())), authorUsername = checkNotNull(it.value2())) }
            }
        }
        val boostId = TimelineMembers.boostIdOf(member)
        if (boostId != null) {
            return jooq.withConnection { dsl ->
                dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.INBOX, REMOTE_NOTES.VISIBILITY)
                    .from(REMOTE_BOOSTS)
                    .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_BOOSTS.REMOTE_NOTE_ID))
                    .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                    .where(REMOTE_BOOSTS.ID.eq(boostId))
                    .and(dsl.remoteBoostReadableBy(accountId))
                    .fetchOne { it.toRemoteTarget() }
            }
        }
        val remoteNoteId = TimelineMembers.remoteNoteIdOf(member) ?: return null
        return jooq.withConnection { dsl ->
            dsl.select(REMOTE_NOTES.OBJECT_URI, REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.INBOX, REMOTE_NOTES.VISIBILITY)
                .from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.ID.eq(remoteNoteId))
                .and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))
                .and(dsl.remoteNoteReadableOrBoostedTo(accountId))
                .fetchOne { it.toRemoteTarget() }
        }
    }

    override fun boostAndEnqueue(boost: OutgoingBoost): TimelinePosition? {
        return jooq.transaction { dsl ->
            val boosted = dsl.boostedNoteOf(boost.member) ?: return@transaction null
            val now = StoredInstant.format(boost.boostedAt)
            val boostId = dsl.insertInto(ACCOUNT_BOOSTS)
                .set(ACCOUNT_BOOSTS.ACCOUNT_ID, boost.accountId.value)
                .set(ACCOUNT_BOOSTS.REMOTE_NOTE_ID, boosted.remoteNoteId)
                .set(ACCOUNT_BOOSTS.NOTE_ID, boosted.localNoteId)
                .set(ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI, boost.announceId)
                .set(ACCOUNT_BOOSTS.ANNOUNCE_BODY, boost.announceBody)
                .set(ACCOUNT_BOOSTS.UNDO_BODY, boost.undoBody)
                .set(ACCOUNT_BOOSTS.OBJECT_URI, boost.objectUri)
                .set(ACCOUNT_BOOSTS.OBJECT_AUTHOR, boost.objectAuthor)
                .set(ACCOUNT_BOOSTS.AUDIENCE, if (boost.unlisted) AccountBoostValues.AUDIENCE_UNLISTED else AccountBoostValues.AUDIENCE_PUBLIC)
                .set(ACCOUNT_BOOSTS.CREATED_AT, now)
                .set(ACCOUNT_BOOSTS.SCORE_MS, boost.boostedAt.toEpochMilli())
                .onConflictDoNothing()
                .returning(ACCOUNT_BOOSTS.ID)
                .fetchOne(ACCOUNT_BOOSTS.ID)
                ?: return@transaction null
            boost.inboxes.distinct().forEach { inbox ->
                dsl.insertInto(ACCOUNT_BOOST_INBOXES)
                    .set(ACCOUNT_BOOST_INBOXES.BOOST_ID, boostId)
                    .set(ACCOUNT_BOOST_INBOXES.INBOX, inbox)
                    .execute()
                dsl.enqueueDelivery(boost.accountId, inbox, boost.announceBody, now, DeliveryQueueValues.KIND_ANNOUNCE)
            }
            TimelinePosition(boost.boostedAt.toEpochMilli(), TimelineMembers.accountBoostMember(boostId))
        }
    }

    override fun unboostAndEnqueueUndo(accountId: AccountId, member: String, now: Instant): Boolean {
        return jooq.transaction { dsl ->
            val boosted = dsl.boostedNoteOf(member) ?: return@transaction false
            val boostId = dsl.select(ACCOUNT_BOOSTS.ID).from(ACCOUNT_BOOSTS)
                .where(ACCOUNT_BOOSTS.ACCOUNT_ID.eq(accountId.value))
                .and(boosted.condition())
                .fetchOne(ACCOUNT_BOOSTS.ID)
                ?: return@transaction false
            dsl.undoAccountBoost(boostId, now)
        }
    }

    override fun deleteRemoteNoteAndUndoBoosts(actorUri: String, objectUri: String, now: Instant): Int {
        return jooq.transaction { dsl ->
            val remoteNoteIds = dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.OBJECT_URI.eq(objectUri))
                .and(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .fetch(REMOTE_NOTES.ID)
                .filterNotNull()
            if (remoteNoteIds.isEmpty()) return@transaction 0
            val undone = dsl.undoAccountBoosts(ACCOUNT_BOOSTS.REMOTE_NOTE_ID.`in`(remoteNoteIds), now)
            dsl.deleteFrom(REMOTE_NOTES).where(REMOTE_NOTES.ID.`in`(remoteNoteIds)).execute()
            undone
        }
    }

    override fun listForOutbox(username: String, after: AccountBoostPosition?, limit: Int): List<AccountBoostRecord> = jooq.withConnection { dsl ->
        dsl.select(ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI, ACCOUNT_BOOSTS.OBJECT_URI, ACCOUNT_BOOSTS.OBJECT_AUTHOR, ACCOUNT_BOOSTS.AUDIENCE, ACCOUNT_BOOSTS.CREATED_AT)
            .from(ACCOUNT_BOOSTS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_BOOSTS.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(olderThan(after))
            .orderBy(ACCOUNT_BOOSTS.CREATED_AT.desc(), ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI.desc())
            .limit(limit)
            .fetch { record ->
                AccountBoostRecord(
                    announceId = checkNotNull(record.value1()),
                    objectUri = checkNotNull(record.value2()),
                    objectAuthor = checkNotNull(record.value3()),
                    unlisted = record.value4() == AccountBoostValues.AUDIENCE_UNLISTED,
                    boostedAt = StoredInstant.parse(checkNotNull(record.value5())),
                )
            }
    }

    override fun countForOutbox(username: String): Long = jooq.withConnection { dsl ->
        dsl.selectCount().from(ACCOUNT_BOOSTS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_BOOSTS.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun boostedLocalNotes(accountId: AccountId, publicIds: Set<PublicNoteId>): Set<PublicNoteId> {
        if (publicIds.isEmpty()) return setOf()
        return jooq.withConnection { dsl ->
            dsl.select(NOTES.PUBLIC_ID).from(ACCOUNT_BOOSTS)
                .join(NOTES).on(NOTES.ID.eq(ACCOUNT_BOOSTS.NOTE_ID))
                .where(ACCOUNT_BOOSTS.ACCOUNT_ID.eq(accountId.value))
                .and(NOTES.PUBLIC_ID.`in`(publicIds.map { it.value }))
                .fetchSet(NOTES.PUBLIC_ID)
                .mapNotNull { it?.let(::PublicNoteId) }
                .toSet()
        }
    }

    /**
     * 時刻だけで比べると、同じ時刻のブーストがページの境目に来たときに落ちるか重なる
     */
    private fun olderThan(position: AccountBoostPosition?): Condition {
        if (position == null) return DSL.noCondition()
        val boostedAt = StoredInstant.format(position.boostedAt)
        return ACCOUNT_BOOSTS.CREATED_AT.lt(boostedAt)
            .or(ACCOUNT_BOOSTS.CREATED_AT.eq(boostedAt).and(ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI.lt(position.announceId)))
    }

    /**
     * ブーストで載った投稿は、ブーストされた元の投稿を指す
     */
    private fun DSLContext.boostedNoteOf(member: String): BoostedNote? {
        val accountBoostId = TimelineMembers.accountBoostIdOf(member)
        if (accountBoostId != null) {
            return select(ACCOUNT_BOOSTS.REMOTE_NOTE_ID, ACCOUNT_BOOSTS.NOTE_ID).from(ACCOUNT_BOOSTS).where(ACCOUNT_BOOSTS.ID.eq(accountBoostId))
                .fetchOne { BoostedNote(remoteNoteId = it.value1(), localNoteId = it.value2()) }
        }
        val localNoteId = TimelineMembers.localNoteIdOf(member)
        if (localNoteId != null) return BoostedNote(remoteNoteId = null, localNoteId = localNoteId)
        val boostId = TimelineMembers.boostIdOf(member)
        val remoteNoteId = if (boostId == null) {
            TimelineMembers.remoteNoteIdOf(member)
        } else {
            select(REMOTE_BOOSTS.REMOTE_NOTE_ID).from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.ID.eq(boostId)).fetchOne(REMOTE_BOOSTS.REMOTE_NOTE_ID)
        }
        return remoteNoteId?.let { BoostedNote(remoteNoteId = it, localNoteId = null) }
    }

    private class BoostedNote(val remoteNoteId: Long?, val localNoteId: Long?) {
        fun condition(): Condition = if (remoteNoteId != null) ACCOUNT_BOOSTS.REMOTE_NOTE_ID.eq(remoteNoteId) else ACCOUNT_BOOSTS.NOTE_ID.eq(localNoteId)
    }

    private fun Record4<String?, String?, String?, String?>.toRemoteTarget(): BoostTarget.Remote = BoostTarget.Remote(
        objectUri = checkNotNull(value1()),
        authorActorUri = checkNotNull(value2()),
        authorInbox = checkNotNull(value3()),
        unlisted = value4() == "unlisted",
    )
}
