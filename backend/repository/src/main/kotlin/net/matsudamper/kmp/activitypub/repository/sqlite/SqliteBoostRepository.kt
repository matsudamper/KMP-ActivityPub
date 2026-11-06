package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_BOOSTS
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

        dsl.insertInto(NOTE_BOOSTS)
            .set(NOTE_BOOSTS.NOTE_ID, noteId)
            .set(NOTE_BOOSTS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_BOOSTS.ACTIVITY_URI, activityUri)
            .set(NOTE_BOOSTS.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflictDoNothing()
            .execute() > 0
    }

    override fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean = jooq.transaction { dsl ->
        val remoteActorId = dsl.remoteActorId(actorUri) ?: return@transaction false
        val removedLocal = dsl.deleteFrom(NOTE_BOOSTS)
            .where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(NOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .execute()
        val removedRemote = dsl.deleteFrom(REMOTE_BOOSTS)
            .where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(REMOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .execute()
        removedLocal + removedRemote > 0
    }

    override fun removeActor(actorUri: String): Int = jooq.transaction { dsl ->
        val remoteActorId = dsl.remoteActorId(actorUri) ?: return@transaction 0
        dsl.deleteFrom(NOTE_BOOSTS).where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId)).execute() +
            dsl.deleteFrom(REMOTE_BOOSTS).where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(remoteActorId)).execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .andExists(dsl.selectOne().from(NOTE_BOOSTS).where(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long> {
        if (notePublicIds.isEmpty()) return mapOf()

        val counted = jooq.withConnection { dsl ->
            dsl.select(NOTES.PUBLIC_ID, DSL.count())
                .from(NOTE_BOOSTS)
                .join(NOTES).on(NOTES.ID.eq(NOTE_BOOSTS.NOTE_ID))
                .where(NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTES.PUBLIC_ID)
                .fetch { PublicNoteId(checkNotNull(it.value1())) to it.value2().toLong() }
                .toMap()
        }
        return notePublicIds.associateWith { counted[it] ?: 0L }
    }

    private fun DSLContext.remoteActorId(actorUri: String): Long? =
        select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID)
}
