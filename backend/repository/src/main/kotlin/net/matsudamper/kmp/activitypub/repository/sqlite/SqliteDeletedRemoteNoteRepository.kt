package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.DeletedRemoteNote
import net.matsudamper.kmp.activitypub.repository.DeletedRemoteNoteRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteDeletedRemoteNoteRepository(
    private val jooq: SqliteJooq,
) : DeletedRemoteNoteRepository {
    override fun page(
        beforeId: Long?,
        limit: Int,
    ): List<DeletedRemoteNote> = jooq.withConnection { dsl ->
        dsl.select(
            REMOTE_NOTES.ID,
            REMOTE_NOTES.OBJECT_URI,
            REMOTE_ACTORS.ACTOR_URI,
            REMOTE_ACTORS.PREFERRED_USERNAME,
            REMOTE_NOTES.CONTENT_HTML,
            REMOTE_NOTES.PUBLISHED_AT,
            REMOTE_NOTES.DELETED_AT,
        )
            .from(REMOTE_NOTES)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .where(REMOTE_NOTES.DELETED_AT.isNotNull)
            .and(if (beforeId == null) DSL.noCondition() else REMOTE_NOTES.ID.lt(beforeId))
            .orderBy(REMOTE_NOTES.ID.desc())
            .limit(limit)
            .fetch { it.toDeletedRemoteNote() }
    }

    override fun purge(id: Long): Boolean = jooq.transaction { dsl ->
        dsl.deleteFrom(REMOTE_NOTES).where(REMOTE_NOTES.ID.eq(id)).and(REMOTE_NOTES.DELETED_AT.isNotNull).execute() > 0
    }

    override fun purgeAll(): Int = jooq.transaction { dsl ->
        dsl.deleteFrom(REMOTE_NOTES).where(REMOTE_NOTES.DELETED_AT.isNotNull).execute()
    }

    private fun Record.toDeletedRemoteNote(): DeletedRemoteNote = DeletedRemoteNote(
        id = checkNotNull(get(REMOTE_NOTES.ID)),
        objectUri = checkNotNull(get(REMOTE_NOTES.OBJECT_URI)),
        actorUri = checkNotNull(get(REMOTE_ACTORS.ACTOR_URI)),
        actorName = get(REMOTE_ACTORS.PREFERRED_USERNAME),
        contentHtml = checkNotNull(get(REMOTE_NOTES.CONTENT_HTML)),
        publishedAt = StoredInstant.parse(checkNotNull(get(REMOTE_NOTES.PUBLISHED_AT))),
        deletedAt = StoredInstant.parse(checkNotNull(get(REMOTE_NOTES.DELETED_AT))),
    )
}
