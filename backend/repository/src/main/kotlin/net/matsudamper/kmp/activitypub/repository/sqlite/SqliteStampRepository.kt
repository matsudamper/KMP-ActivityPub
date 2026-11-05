package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_STAMPS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class SqliteStampRepository(
    private val jooq: SqliteJooq,
) : StampRepository {
    override fun put(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        emoji: String,
        emojiImageUrl: String?,
        receivedAt: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val noteId = dsl.findNoteId(notePublicId) ?: return@transaction false
        val remoteActorId = dsl.upsertRemoteActor(actor, updatedAt = receivedAt)
        dsl.insertInto(NOTE_STAMPS)
            .set(NOTE_STAMPS.NOTE_ID, noteId)
            .set(NOTE_STAMPS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_STAMPS.EMOJI, emoji)
            .set(NOTE_STAMPS.EMOJI_IMAGE_URL, emojiImageUrl)
            .set(NOTE_STAMPS.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflict(NOTE_STAMPS.NOTE_ID, NOTE_STAMPS.REMOTE_ACTOR_ID, NOTE_STAMPS.EMOJI)
            .doUpdate()
            .set(NOTE_STAMPS.EMOJI_IMAGE_URL, emojiImageUrl)
            .execute()
        true
    }

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTE_STAMPS)
            .where(NOTE_STAMPS.NOTE_ID.eq(dsl.select(NOTES.ID).from(NOTES).where(NOTES.PUBLIC_ID.eq(notePublicId.value))))
            .and(NOTE_STAMPS.REMOTE_ACTOR_ID.eq(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
            .and(NOTE_STAMPS.EMOJI.eq(emoji))
            .execute() > 0
    }

    override fun removeActor(actorUri: String): Int = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTE_STAMPS)
            .where(NOTE_STAMPS.REMOTE_ACTOR_ID.eq(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
            .execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .andExists(dsl.selectOne().from(NOTE_STAMPS).where(NOTE_STAMPS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>> {
        if (notePublicIds.isEmpty()) return mapOf()

        val counted = jooq.withConnection { dsl ->
            dsl.stampCountsBy(NOTES.PUBLIC_ID, NOTES.PUBLIC_ID.`in`(notePublicIds.map { it.value }))
        }.mapKeys { PublicNoteId(it.key) }
        return notePublicIds.associateWith { counted[it].orEmpty() }
    }
}
