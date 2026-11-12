package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.EARLY_UNDONE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_FAVOURITES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTIFICATIONS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

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

        val inserted = dsl.insertInto(NOTE_FAVOURITES)
            .set(NOTE_FAVOURITES.NOTE_ID, noteId)
            .set(NOTE_FAVOURITES.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_FAVOURITES.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflictDoNothing()
            .execute() > 0
        if (inserted) dsl.insertNotification(NotificationKind.FAVOURITE, noteId, remoteActorId, receivedAt)
        inserted
    }

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = jooq.transaction { dsl ->
        val noteId = dsl.findNoteId(notePublicId) ?: return@transaction false
        val remoteActorId = dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID)
            ?: return@transaction false
        dsl.deleteNotification(NotificationKind.FAVOURITE, noteId, remoteActorId)
        dsl.deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.NOTE_ID.eq(noteId))
            .and(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(remoteActorId))
            .execute() > 0
    }

    override fun removeActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl.deleteFrom(NOTIFICATIONS)
            .where(NOTIFICATIONS.REMOTE_ACTOR_ID.eq(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
            .and(NOTIFICATIONS.KIND.eq(NotificationKind.FAVOURITE.stored))
            .execute()
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
            dsl.select(NOTES.PUBLIC_ID, dsl.favouriteCountOf(NOTES.ID))
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .fetch { PublicNoteId(checkNotNull(it.value1())) to checkNotNull(it.value2()) }
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
