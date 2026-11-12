package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.BoostRemoval
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTIFICATIONS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqliteBoostRepository(
    private val jooq: SqliteJooq,
) : BoostRepository {
    override fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        activityUri: String,
        receivedAt: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val noteId = dsl.findNoteId(notePublicId) ?: return@transaction false
        val remoteActorId = dsl.upsertRemoteActor(actor, updatedAt = receivedAt)

        val inserted = dsl.insertInto(NOTE_BOOSTS)
            .set(NOTE_BOOSTS.NOTE_ID, noteId)
            .set(NOTE_BOOSTS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_BOOSTS.ACTIVITY_URI, activityUri)
            .set(NOTE_BOOSTS.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflictDoNothing()
            .execute() > 0
        if (inserted) dsl.insertNotification(NotificationKind.BOOST, noteId, remoteActorId, receivedAt)
        inserted
    }

    override fun remove(
        actorUri: String,
        activityUri: String,
        undoneAt: Instant,
    ): BoostRemoval? = jooq.transaction { dsl ->
        val remoteActorId = dsl.remoteActorId(actorUri) ?: return@transaction null
        val boostedNoteIds = dsl.select(NOTE_BOOSTS.NOTE_ID).from(NOTE_BOOSTS)
            .where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(NOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .fetch(NOTE_BOOSTS.NOTE_ID)
        dsl.deleteFrom(NOTE_BOOSTS)
            .where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(NOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .execute()
        boostedNoteIds.forEach { noteId -> dsl.deleteNotification(NotificationKind.BOOST, checkNotNull(noteId), remoteActorId) }
        val undoneBoostId = dsl.select(REMOTE_BOOSTS.ID).from(REMOTE_BOOSTS)
            .where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(REMOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .and(REMOTE_BOOSTS.UNDONE_AT.isNull)
            .fetchOne(REMOTE_BOOSTS.ID)
        if (undoneBoostId != null) {
            dsl.update(REMOTE_BOOSTS).set(REMOTE_BOOSTS.UNDONE_AT, StoredInstant.format(undoneAt)).where(REMOTE_BOOSTS.ID.eq(undoneBoostId)).execute()
        }
        if (boostedNoteIds.isEmpty() && undoneBoostId == null) {
            null
        } else {
            BoostRemoval(undoneTimelineMember = undoneBoostId?.let { "b$it" })
        }
    }

    override fun removeActor(actorUri: String): Int = jooq.transaction { dsl ->
        val remoteActorId = dsl.remoteActorId(actorUri) ?: return@transaction 0
        dsl.deleteFrom(NOTIFICATIONS).where(NOTIFICATIONS.REMOTE_ACTOR_ID.eq(remoteActorId)).and(NOTIFICATIONS.KIND.eq(NotificationKind.BOOST.stored)).execute()
        dsl.deleteFrom(NOTE_BOOSTS).where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId)).execute() +
            dsl.deleteFrom(REMOTE_BOOSTS).where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId)).execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .and(
                DSL.exists(dsl.selectOne().from(NOTE_BOOSTS).where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
                    .or(DSL.exists(dsl.selectOne().from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))),
            )
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long> {
        if (notePublicIds.isEmpty()) return mapOf()

        // 他のサーバーから届いたブーストと、このサーバーのアカウントのブーストを合わせる
        val counted = jooq.withConnection { dsl ->
            listOf(NOTE_BOOSTS.NOTE_ID, ACCOUNT_BOOSTS.NOTE_ID).map { noteIdField ->
                dsl.select(NOTES.PUBLIC_ID, DSL.count())
                    .from(noteIdField.table)
                    .join(NOTES).on(NOTES.ID.eq(noteIdField))
                    .where(NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                    .groupBy(NOTES.PUBLIC_ID)
                    .fetch { PublicNoteId(checkNotNull(it.value1())) to it.value2().toLong() }
            }.flatten().groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        }
        return notePublicIds.associateWith { counted[it] ?: 0L }
    }

    private fun DSLContext.remoteActorId(actorUri: String): Long? =
        select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID)
}
