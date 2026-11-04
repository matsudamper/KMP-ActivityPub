package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.EARLY_UNDONE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_FAVOURITES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteFavouriteRepository(
    private val jooq: SqliteJooq,
) : FavouriteRepository {
    override fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        receivedAt: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val noteId = dsl.findNoteId(notePublicId) ?: return@transaction false
        val remoteActorId = dsl.upsertRemoteActor(actor, updatedAt = receivedAt)

        dsl.insertInto(NOTE_FAVOURITES)
            .set(NOTE_FAVOURITES.NOTE_ID, noteId)
            .set(NOTE_FAVOURITES.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_FAVOURITES.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflictDoNothing()
            .execute() > 0
    }

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.NOTE_ID.eq(dsl.select(NOTES.ID).from(NOTES).where(NOTES.PUBLIC_ID.eq(notePublicId.value))))
            .and(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
            .execute() > 0
    }

    override fun removeActor(actorUri: String): Int = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
            .execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .andExists(dsl.selectOne().from(NOTE_FAVOURITES).where(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long> {
        if (notePublicIds.isEmpty()) return mapOf()

        val counted = jooq.withConnection { dsl ->
            dsl.select(NOTES.PUBLIC_ID, DSL.count())
                .from(NOTE_FAVOURITES)
                .join(NOTES).on(NOTES.ID.eq(NOTE_FAVOURITES.NOTE_ID))
                .where(NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTES.PUBLIC_ID)
                .fetch { PublicNoteId(checkNotNull(it.value1())) to it.value2().toLong() }
                .toMap()
        }
        return notePublicIds.associateWith { counted[it] ?: 0L }
    }

    override fun rememberEarlyUndone(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        jooq.transaction { dsl ->
            // 期限の切れた行を読むことは無いので、覚えるついでに捨てる
            dsl.deleteFrom(EARLY_UNDONE_LIKES)
                .where(EARLY_UNDONE_LIKES.EXPIRES_AT.lt(StoredInstant.format(Instant.now())))
                .execute()
            dsl.insertInto(EARLY_UNDONE_LIKES)
                .set(EARLY_UNDONE_LIKES.ACTOR_URI, actorUri)
                .set(EARLY_UNDONE_LIKES.ACTIVITY_URI, activityUri)
                .set(EARLY_UNDONE_LIKES.EXPIRES_AT, StoredInstant.format(expiresAt))
                .onConflict(EARLY_UNDONE_LIKES.ACTOR_URI, EARLY_UNDONE_LIKES.ACTIVITY_URI)
                .doUpdate()
                .set(EARLY_UNDONE_LIKES.EXPIRES_AT, StoredInstant.format(expiresAt))
                .execute()
        }
    }

    override fun isEarlyUndone(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(
            dsl.selectOne()
                .from(EARLY_UNDONE_LIKES)
                .where(EARLY_UNDONE_LIKES.ACTOR_URI.eq(actorUri))
                .and(EARLY_UNDONE_LIKES.ACTIVITY_URI.eq(activityUri))
                .and(EARLY_UNDONE_LIKES.EXPIRES_AT.gt(StoredInstant.format(now))),
        )
    }
}
